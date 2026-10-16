@file:JvmName("Steamed")
@file:JvmMultifileClass

package xyz.kkaii.steamed

import com.codedisaster.steamworks.*
import xyz.kkaii.steamed.lib.Option
import xyz.kkaii.steamed.mixin.ConnectScreenAccessor
import xyz.kkaii.steamed.net.SteamAuthHandshake
import xyz.kkaii.steamed.net.SteamP2PRelay
import xyz.kkaii.steamed.net.SteamP2PVoiceRelay
import xyz.kkaii.steamed.net.PlasmoVoiceIntegration
import xyz.kkaii.steamed.net.PublicTunnelMods
import xyz.kkaii.steamed.net.SvcIntegration
import xyz.kkaii.steamed.screen.SteamLobbyScreen
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.DisconnectedScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.client.server.IntegratedServer
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.util.HttpUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
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

    @JvmField
    var authHandshake: SteamAuthHandshake? = null

    // Read by IntegratedServerMixin while startP2PGame publishes the world.
    @JvmField
    @Volatile
    var publishingForSteam = false

    private var steamPublishedServer: WeakReference<IntegratedServer>? = null

    @Volatile
    var hostedPort: Int? = null

    // UDP ports of voice mods running on the hosted world, tunneled over SteamP2PVoiceRelay.
    @Volatile
    var hostedVoicePorts: List<Int> = emptyList()

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
    }

    data class Invite(val lobby: SteamID, val inviter: SteamID, val inviterName: String)

    // Invites only notify; joining needs the player to accept in the lobby screen.
    @Volatile
    var pendingInvite: Invite? = null

    private val INVITE_TOAST = SystemToast.SystemToastId(10000L)

    fun onLobbyInvite(lobby: SteamID, inviter: SteamID) {
        if (lobbyId.isSome() && lobbyId.unwrap() == lobby) return
        val name = friends?.getFriendPersonaName(inviter) ?: "Someone"
        pendingInvite = Invite(SteamID(lobby), SteamID(inviter), name)
        val mc = Minecraft.getInstance()
        mc.execute {
            SystemToast.addOrUpdate(
                mc.gui.toastManager(), INVITE_TOAST,
                Component.literal("$name invited you to a Steam lobby"),
                Component.literal("Open Steam Lobby to accept")
            )
            mc.gui.hud.chat.addClientSystemMessage(
                Component.literal("$name invited you to their Steam lobby. Open Steam Lobby from the pause or title screen to accept")
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
        p2pRelay?.closePeer(peer)
    }

    fun unkick(peer: SteamID) {
        kickedPeers.remove(peer)
    }

    fun onLobbyChatMessage(lobby: SteamID, sender: SteamID, message: String) {
        if (matchmaking?.getLobbyOwner(lobby) != sender || sender == user?.steamID) return

        val reason = when {
            message == CLOSE_MESSAGE -> "The host closed the Steam lobby"
            message.startsWith(KICK_PREFIX) -> {
                val target = message.removePrefix(KICK_PREFIX).toLongOrNull() ?: return
                if (target != user?.steamID?.let { SteamNativeHandle.getNativeHandle(it) }) return
                "The host removed you from the Steam lobby"
            }
            else -> return
        }

        LOGGER.info("Leaving lobby {}: {}", lobby, reason)
        val mc = Minecraft.getInstance()
        mc.execute {
            val connectedToHost = isConnectedToLobbyHost()
            leaveLobby()
            val text = Component.literal(reason)
            if (connectedToHost) {
                mc.disconnect(DisconnectedScreen(TitleScreen(), Component.literal("Disconnected"), text), false)
            } else if (mc.level != null) {
                mc.gui.hud.chat.addClientSystemMessage(text)
            }
        }
    }

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
        hostedVoicePorts = emptyList()
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
            mc.gui.hud.chat.addClientSystemMessage(
                Component.literal("Load a world before creating a Steam lobby")
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
                Component.literal("Hosting through Steam")
            } else {
                // Opened to LAN by the player; reuse it rather than failing. It stays reachable on the LAN.
                Component.literal("Hosting through Steam (this world is also open to your LAN)")
            }
        } else {
            port = HttpUtil.getAvailablePort()
            publishingForSteam = true
            val published = try {
                server.publishServer(MinecraftServer.MultiplayerScope.LAN, server.defaultGameType, true, port)
            } finally {
                publishingForSteam = false
            }
            if (!published) {
                LOGGER.error("Unable to start local game server, (E03: publishServer failed)")
                mc.gui.hud.chat.addClientSystemMessage(Component.translatable("commands.publish.failed"))
                return
            }
            steamPublishedServer = WeakReference(server)
            message = Component.literal("Hosting through Steam")
        }

        hostedPort = port
        lobbyWorldStarted = true
        p2pRelay?.start()

        // Joiners learn these from the auth handshake's accepted reply, not from the lobby.
        hostedVoicePorts = listOfNotNull(SvcIntegration.detectPort(), PlasmoVoiceIntegration.detectPort()).distinct()
        LOGGER.info("Tunneling voice ports: {}", hostedVoicePorts)

        matchmaking?.setLobbyGameServer(lobbyId.unwrap(), 0, 0, user!!.steamID)
        mc.gui.hud.chat.addClientSystemMessage(message)

        val tunnels = PublicTunnelMods.installed()
        if (tunnels.isNotEmpty()) {
            LOGGER.warn("Public tunnel mods installed alongside Steam hosting: {}", tunnels)
            mc.gui.hud.chat.addClientSystemMessage(
                Component.literal(
                    "${tunnels.joinToString(" and ")} may also have opened this world to the internet. " +
                        "Turn off its hosting if only your Steam friends should join"
                ).withStyle(ChatFormatting.GOLD)
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

        val hostName = friends?.getFriendPersonaName(hostSteamId) ?: "the host"
        LOGGER.info("Connecting to host {} ({}) over Steam P2P", hostName, hostSteamId.accountID)

        val instance: Minecraft = Minecraft.getInstance()
        instance.execute {
            instance.gui.hud.chat.addClientSystemMessage(Component.literal("Establishing connection with $hostName..."))
        }

        relay.start()
        handshake.requestAccess(
            hostSteamId,
            onAccepted = { payload ->
                val voicePorts = String(payload, StandardCharsets.US_ASCII).split(',').mapNotNull { it.toIntOrNull() }
                openBridgeToHost(relay, hostSteamId, hostName, voicePorts)
            },
            onDenied = { reason ->
                LOGGER.warn("Host {} refused the connection: {}", hostSteamId.accountID, reason)
                instance.execute {
                    instance.gui.hud.chat.addClientSystemMessage(Component.literal("Couldn't join $hostName: $reason"))
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
                    LOGGER.warn("Failed to set up a voice bridge for {} on port {}", hostSteamId.accountID, voicePort, e)
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
            val parent = instance.gui.screen() ?: SteamLobbyScreen(null)
            lobbyWorldStarted = true
            ConnectScreen.startConnecting(parent, instance, address, serverData, false, null)
            (instance.gui.screen() as? ConnectScreenAccessor)
                ?.`steamed$updateStatus`(Component.literal("Establishing connection with $hostName..."))
        }
    }

    fun joinHostedLobby() {
        if (!lobbyId.isSome()) {
            Minecraft.getInstance().execute {
                Minecraft.getInstance().gui.hud.chat.addClientSystemMessage(Component.literal("You're not in a lobby"))
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
                Minecraft.getInstance().gui.hud.chat.addClientSystemMessage(Component.literal("The host hasn't started their world yet"))
            }
            return
        }

        if (outServer == user?.steamID) {
            Minecraft.getInstance().execute {
                Minecraft.getInstance().gui.hud.chat.addClientSystemMessage(Component.literal("You're the host"))
            }
            return
        }

        connectToHost(outServer)
    }
}
