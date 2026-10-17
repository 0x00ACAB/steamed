@file:JvmName("Steamed")
@file:JvmMultifileClass

package xyz.kkaii.steamed

import com.codedisaster.steamworks.*
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.DisconnectedScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.client.resources.language.I18n
import net.minecraft.client.server.IntegratedServer
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.HttpUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import xyz.kkaii.steamed.compat.*
import xyz.kkaii.steamed.lib.Option
import xyz.kkaii.steamed.mixin.ConnectScreenAccessor
import xyz.kkaii.steamed.net.*
import xyz.kkaii.steamed.screen.SteamConnectingScreen
import xyz.kkaii.steamed.screen.SteamLobbyScreen
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.Volatile
import kotlin.concurrent.thread

object Steamed {
    val LOGGER: Logger = LoggerFactory.getLogger("steamp2p")

    private const val KICK_PREFIX = "steamed:kick:"
    private const val CLOSE_MESSAGE = "steamed:close"
    private const val LOBBY_SERVER_NAME = "Steam lobby"

    @JvmField
    var user: SteamUser? = null

    @JvmField
    var userStats: SteamUserStats? = null

    @JvmField
    var friends: SteamFriends? = null

    @JvmField
    var matchmaking: SteamMatchmaking? = null

    @JvmField
    var networking: SteamNetworking? = null

    @JvmField
    var p2pRelay: SteamP2PRelay? = null

    @JvmField
    var p2pVoiceRelay: SteamP2PVoiceRelay? = null

    var p2pWebRelay: SteamP2PWebRelay? = null

    @JvmField
    var authHandshake: SteamAuthHandshake? = null

    // Read by IntegratedServerMixin while startP2PGame publishes the world.
    @JvmField
    @Volatile
    var publishingForSteam = false

    private var steamPublishedServer: WeakReference<IntegratedServer>? = null

    @Volatile
    var hostedPort: Int? = null

    // The lobby's member limit while hosting, which IntegratedServerMixin uses in place of vanilla's 8.
    @JvmField
    @Volatile
    var hostedMaxPlayers: Int = 0

    // UDP ports of voice mods running on the hosted world, tunneled over SteamP2PVoiceRelay.
    @Volatile
    var hostedVoicePorts: List<Int> = emptyList()

    // Web maps found when hosting started, offered to each joiner once authenticated.
    @Volatile
    var hostedWebMaps: List<WebMapIntegration.WebMap> = emptyList()

    @Volatile
    var lobbyId: Option<SteamID?> = Option()

    var timer: Timer? = null

    // Steam has no way for a lobby owner to remove a member, so a kick is enforced at the
    // P2P layer instead: SteamNetwork refuses sessions from anyone in here.
    val kickedPeers: MutableSet<SteamID> = ConcurrentHashMap.newKeySet()

    // Set once this lobby's world is hosted or joined, so the title screen only leaves the lobby when that world closed.
    @Volatile
    var lobbyWorldStarted = false

    fun onTitleScreen() {
        if (lobbyWorldStarted) leaveLobby()
        SteamInit.warnIfUnavailable()
    }

    // The type the lobby screen last asked Steam to create, so onLobbyCreated can record it as lobby data.
    @Volatile
    var creatingLobbyType: SteamMatchmaking.LobbyType = SteamMatchmaking.LobbyType.FriendsOnly

    data class Invite(val lobby: SteamID, val inviter: SteamID, val inviterName: String)

    // Invites only notify; joining needs the player to accept in the lobby screen.
    @Volatile
    var pendingInvite: Invite? = null

    private val INVITE_TOAST = SystemToast.SystemToastId(10000L)

    fun onLobbyInvite(lobby: SteamID, inviter: SteamID) {
        if (lobbyId.isSome() && lobbyId.unwrap() == lobby) return
        val name = friends?.getFriendPersonaName(inviter) ?: I18n.get("steamed.someone")
        pendingInvite = Invite(SteamID(lobby), SteamID(inviter), name)
        val mc = Minecraft.getInstance()
        val inLobby = lobbyId.isSome()
        mc.execute {
            mc.toast(INVITE_TOAST,
                Component.translatable("steamed.invite.toast", name),
                Component.translatable(if (inLobby) "steamed.invite.toast.leave_first" else "steamed.invite.toast.open_lobby")
            )
            mc.systemMessage(
                Component.translatable(
                    if (inLobby) "steamed.invite.chat.leave_first" else "steamed.invite.chat.open_lobby", name
                )
            )
        }
    }

    fun acceptInvite() {
        val invite = pendingInvite ?: return
        pendingInvite = null
        joinLobby(invite.lobby)
    }

    fun declineInvite() {
        pendingInvite = null
    }

    fun isLobbyOwner(): Boolean =
        lobbyId.isSome() && matchmaking?.getLobbyOwner(lobbyId.unwrap()) == user?.steamID

    fun kick(peer: SteamID) {
        if (!isLobbyOwner()) return
        val lobby = lobbyId.unwrap()

        kickedPeers.add(SteamID(peer))
        matchmaking?.sendLobbyChatMsg(lobby, KICK_PREFIX + SteamNativeHandle.getNativeHandle(peer))
        dropPeer(peer)
        LOGGER.info("Kicked {} from lobby {}", peer.accountID, lobby)
    }

    // Host side: a member left the lobby, so they no longer pass SteamNetwork's membership check.
    fun onMemberLeft(lobby: SteamID, peer: SteamID) {
        if (!isLobbyOwner() || lobbyId.unwrap() != lobby || peer == user?.steamID) return
        LOGGER.info("{} left lobby {}, closing their connection", peer.accountID, lobby)
        dropPeer(peer)
    }

    private fun dropPeer(peer: SteamID) {
        authHandshake?.endPeer(peer)
        p2pVoiceRelay?.closePeer(peer)
        p2pWebRelay?.closePeer(peer)
        p2pRelay?.closePeer(peer)
    }

    fun unkick(peer: SteamID) {
        kickedPeers.remove(peer)
    }

    fun onLobbyChatMessage(lobby: SteamID, sender: SteamID, message: String) {
        if (matchmaking?.getLobbyOwner(lobby) != sender || sender == user?.steamID) return

        val reason = when {
            message == CLOSE_MESSAGE -> "steamed.lobby.closed"
            message.startsWith(KICK_PREFIX) -> {
                val target = message.removePrefix(KICK_PREFIX).toLongOrNull() ?: return
                if (target != user?.steamID?.let { SteamNativeHandle.getNativeHandle(it) }) return
                "steamed.lobby.kicked"
            }

            else -> return
        }

        LOGGER.info("Leaving lobby {}: {}", lobby, reason)
        val mc = Minecraft.getInstance()
        mc.execute {
            val connectedToHost = isConnectedToLobbyHost()
            leaveLobby()
            val text = Component.translatable(reason)
            if (connectedToHost) {
                mc.disconnectTo(DisconnectedScreen(TitleScreen(), Component.translatable("steamed.disconnected"), text))
            } else if (mc.level != null) {
                mc.systemMessage(text)
            }
        }
    }

    fun denialText(denial: Denial): Component =
        Component.translatableWithFallback(denial.key, denial.key, *denial.args.toTypedArray())

    fun isConnectedToLobbyHost(): Boolean =
        Minecraft.getInstance().getCurrentServer()?.name == LOBBY_SERVER_NAME

    fun joinLobby(lobby: SteamID) {
        if (lobbyId.isSome()) {
            if (lobbyId.unwrap() == lobby) return
            leaveLobby()
        }
        if (pendingInvite?.lobby == lobby) pendingInvite = null
        lobbyId.some(SteamID(lobby))
        matchmaking?.joinLobby(lobby)
    }

    fun leaveLobby() {
        authHandshake?.stop()
        p2pRelay?.stop()
        p2pVoiceRelay?.stop()
        p2pWebRelay?.stop()
        p2pWebRelay?.expectedHost = null

        if (lobbyId.isSome()) {
            val lobby = lobbyId.unwrap()
            // Otherwise Steam hands ownership to a remaining member, who has no world to host.
            if (isLobbyOwner()) matchmaking?.sendLobbyChatMsg(lobby, CLOSE_MESSAGE)
            matchmaking?.leaveLobby(lobby)
            LOGGER.info("Left lobby {}", lobby)
        }

        lobbyId.some(null)
        lobbyWorldStarted = false
        SteamPlayers.clear()
        kickedPeers.clear()
        hostedPort = null
        hostedMaxPlayers = 0
        hostedVoicePorts = emptyList()
        hostedWebMaps = emptyList()
    }

    fun startP2PGame() {
        // create a socket

        val mc: Minecraft = Minecraft.getInstance()
        if (!lobbyId.isSome()) {
            LOGGER.error(
                "Unable to start local game server, (E02: no lobby created)"
            )
            return
        }

        if (!mc.allowsMultiplayer() || !mc.hasSingleplayerServer()) {
            LOGGER.error(
                "Unable to start local game server, (E01: multiplayer not allowed)"
            )
            mc.systemMessage(
                Component.translatable("steamed.host.no_world")
            )
            return
        }


        val server: IntegratedServer = mc.singleplayerServer!!

        val port: Int
        val message: Component
        if (server.isPublished) {
            port = server.port
            message = if (steamPublishedServer?.get() === server) {
                // Published by an earlier lobby for this same world, so still loopback-only.
                Component.translatable("steamed.host.started")
            } else {
                // Opened to LAN by the player; reuse it rather than failing. It stays reachable on the LAN.
                Component.translatable("steamed.host.started_lan")
            }
        } else {
            port = HttpUtil.getAvailablePort()
            publishingForSteam = true
            val published = try {
                publishForSteam(server, port)
            } finally {
                publishingForSteam = false
            }
            if (!published) {
                LOGGER.error("Unable to start local game server, (E03: publishServer failed)")
                mc.systemMessage(Component.translatable("commands.publish.failed"))
                return
            }
            steamPublishedServer = WeakReference(server)
            message = Component.translatable("steamed.host.started")
        }

        hostedPort = port
        hostedMaxPlayers = matchmaking?.getLobbyMemberLimit(lobbyId.unwrap()) ?: 0
        lobbyWorldStarted = true
        p2pRelay?.start()

        // Joiners learn these from the auth handshake's accepted reply, not from the lobby.
        hostedVoicePorts = listOfNotNull(SvcIntegration.detectPort(), PlasmoVoiceIntegration.detectPort()).distinct()
        LOGGER.info("Tunneling voice ports: {}", hostedVoicePorts)
        thread(name = "steamed-web-map-detect", isDaemon = true) {
            val maps = WebMapIntegration.detect()
            // Leaving the lobby while this ran clears hostedPort; don't bring the maps back after that.
            if (hostedPort == port) hostedWebMaps = maps
            LOGGER.info("Web maps for joiners: {}", maps)
        }

        matchmaking?.setLobbyGameServer(lobbyId.unwrap(), 0, 0, user!!.steamID)
        mc.systemMessage(message)

        val tunnels = PublicTunnelMods.installed()
        if (tunnels.isNotEmpty()) {
            LOGGER.warn("Public tunnel mods installed alongside Steam hosting: {}", tunnels)
            mc.systemMessage(
                Component.translatable("steamed.host.public_tunnel", tunnels.joinToString(", ")).withStyle(ChatFormatting.GOLD)
            )
        }
    }

    fun connectToHost(hostSteamId: SteamID) {
        val relay = p2pRelay
        if (relay == null) {
            LOGGER.error("No P2P relay available, cannot connect to {}", hostSteamId.accountID)
            return
        }

        val handshake = authHandshake
        if (handshake == null) {
            LOGGER.error("No auth handshake available, cannot connect to {}", hostSteamId.accountID)
            return
        }

        val hostName = friends?.getFriendPersonaName(hostSteamId) ?: I18n.get("steamed.host.unknown")
        LOGGER.info("Connecting to host {} ({}) over Steam P2P", hostName, hostSteamId.accountID)

        val instance: Minecraft = Minecraft.getInstance()
        instance.execute {
            val current = instance.currentScreen()
            val parent = if (current is SteamConnectingScreen) current.lastScreen else current
            instance.show(SteamConnectingScreen(parent, hostName))
        }

        relay.start()
        p2pWebRelay?.let {
            it.expectedHost = SteamID(hostSteamId)
            it.start()
        }
        handshake.requestAccess(
            hostSteamId,
            onAccepted = { payload ->
                val voicePorts = VoicePorts.decode(payload)
                openBridgeToHost(relay, hostSteamId, hostName, voicePorts)
            },
            onDenied = { reason ->
                LOGGER.warn("Host {} refused the connection: {}", hostSteamId.accountID, reason)
                instance.execute {
                    val screen = instance.currentScreen()
                    if (screen is SteamConnectingScreen) {
                        screen.fail(denialText(reason))
                    } else if (lobbyId.isSome()) {
                        instance.systemMessage(
                            Component.translatable("steamed.join.failed", hostName, denialText(reason))
                        )
                    }
                }
            },
        )
    }

    private fun openBridgeToHost(relay: SteamP2PRelay, hostSteamId: SteamID, hostName: String, voicePorts: List<Int>) {
        val serverSocket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val localPort = serverSocket.localPort

        val voiceRelay = p2pVoiceRelay
        if (voicePorts.isNotEmpty() && voiceRelay != null) {
            voiceRelay.start()
            // A previous attempt's bridges would still hold the ports below.
            voiceRelay.closePeer(hostSteamId)
            for (voicePort in voicePorts) {
                try {
                    // Voice mods on an integrated server tell their clients to use the Minecraft
                    // connection's address (this loopback bridge) with the host's real voice port,
                    // so the bridge must listen on that exact port number.
                    val voiceSocket = DatagramSocket(voicePort, InetAddress.getLoopbackAddress())
                    voiceRelay.addPeer(hostSteamId, voicePort, voiceSocket)
                } catch (e: IOException) {
                    LOGGER.warn(
                        "Failed to set up a voice bridge for {} on port {}",
                        hostSteamId.accountID,
                        voicePort,
                        e
                    )
                }
            }
        }

        thread(name = "steamed-p2p-accept", isDaemon = true) {
            try {
                val client = serverSocket.accept()
                relay.addPeer(hostSteamId, client)
            } catch (e: IOException) {
                LOGGER.error("Failed to accept the local P2P bridge connection", e)
            } finally {
                serverSocket.close()
            }
        }

        val instance: Minecraft = Minecraft.getInstance()
        instance.execute {
            val address = ServerAddress("127.0.0.1", localPort)
            val serverData = ServerData(LOBBY_SERVER_NAME, "127.0.0.1:$localPort", ServerData.Type.LAN)
            val current = instance.currentScreen()
            val parent =
                (if (current is SteamConnectingScreen) current.lastScreen else current) ?: SteamLobbyScreen(null)
            lobbyWorldStarted = true
            ConnectScreen.startConnecting(parent, instance, address, serverData, false, null)
            (instance.currentScreen() as? ConnectScreenAccessor)
                ?.`steamed$updateStatus`(Component.translatable("steamed.join.establishing", hostName))
        }
    }

    // Joiner: the host offered web maps. Posted once we're in the world, since chat from before joining scrolls away.
    fun onMapsAvailable(host: SteamID, maps: List<Pair<String, Int>>) {
        if (maps.isEmpty()) return
        val hostName = friends?.getFriendPersonaName(host) ?: I18n.get("steamed.host.unknown_capitalized")
        val mc = Minecraft.getInstance()
        thread(name = "steamed-web-map-notice", isDaemon = true) {
            val deadline = System.currentTimeMillis() + 60_000
            while (mc.player == null && System.currentTimeMillis() < deadline) Thread.sleep(500)
            mc.execute {
                val message = Component.translatable("steamed.map.available", hostName)
                maps.forEachIndexed { i, (name, port) ->
                    if (i > 0) message.append(" ")
                    val url = "http://127.0.0.1:$port/"
                    message.append(
                        Component.translatable("steamed.map.open", name).withStyle(
                            Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true)
                                .opensUrl(url, Component.translatable("steamed.map.open.hover", url))
                        )
                    )
                }
                mc.systemMessage(message)
            }
        }
    }

    fun joinHostedLobby() {
        if (!lobbyId.isSome()) {
            Minecraft.getInstance().execute {
                Minecraft.getInstance().systemMessage(Component.translatable("steamed.join.not_in_lobby"))
            }
            return
        }

        val outIp = IntArray(1)
        val outPort = ShortArray(1)
        val outServer = SteamID()
        val hasServer = matchmaking?.getLobbyGameServer(lobbyId.unwrap(), outIp, outPort, outServer) ?: false

        if (!hasServer) {
            LOGGER.error("Lobby has no game server set yet")
            Minecraft.getInstance().execute {
                Minecraft.getInstance().systemMessage(Component.translatable("steamed.join.not_started"))
            }
            return
        }

        if (outServer == user?.steamID) {
            Minecraft.getInstance().execute {
                Minecraft.getInstance().systemMessage(Component.translatable("steamed.join.you_are_host"))
            }
            return
        }

        // Invites skip the lobby list's check, so catch a different Minecraft here, before the auth handshake.
        val lobby = lobbyId.unwrap()
        if (!FriendLobbies.sameMinecraft(matchmaking?.getLobbyData(lobby, FriendLobbies.MC_PROTOCOL_KEY))) {
            val hostVersion = matchmaking?.getLobbyData(lobby, FriendLobbies.MC_VERSION_KEY).orEmpty()
            LOGGER.warn("Host is on Minecraft {}, not joining", hostVersion)
            Minecraft.getInstance().execute {
                Minecraft.getInstance().systemMessage(
                    Component.translatable(
                        "steamed.join.wrong_minecraft", hostVersion, minecraftVersionName()
                    ).withStyle(ChatFormatting.RED)
                )
            }
            return
        }

        connectToHost(outServer)
    }
}
