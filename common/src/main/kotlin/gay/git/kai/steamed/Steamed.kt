@file:JvmName("Steamed")
@file:JvmMultifileClass

package gay.git.kai.steamed

import com.codedisaster.steamworks.*
import gay.git.kai.steamed.lib.Option
import gay.git.kai.steamed.mixin.ConnectScreenAccessor
import gay.git.kai.steamed.net.SteamAuthHandshake
import gay.git.kai.steamed.net.SteamP2PRelay
import gay.git.kai.steamed.net.SteamP2PVoiceRelay
import gay.git.kai.steamed.net.SvcIntegration
import gay.git.kai.steamed.screen.SteamLobbyScreen
import net.minecraft.client.Minecraft
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

    @Volatile
    var hostedVoicePort: Int? = null

    @Volatile
    var lobbyId: Option<SteamID?> = Option()

    var timer: Timer? = null

    // Steam has no way for a lobby owner to remove a member, so a kick is enforced at the
    // P2P layer instead: SteamNetwork refuses sessions from anyone in here.
    val kickedPeers: MutableSet<SteamID> = ConcurrentHashMap.newKeySet()

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
        kickedPeers.clear()
        hostedPort = null
        hostedVoicePort = null
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
        p2pRelay?.start()

        val voicePort = SvcIntegration.detectPort() ?: 0
        hostedVoicePort = voicePort

        matchmaking?.setLobbyGameServer(
            lobbyId.unwrap(),
            0,
            voicePort.toShort(),
            user!!.steamID
        )
        mc.gui.hud.chat.addClientSystemMessage(message)
    }

    fun connectToHost(hostSteamId: SteamID, voicePort: Int = 0) {
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
            onAccepted = { openBridgeToHost(relay, hostSteamId, hostName, voicePort) },
            onDenied = { reason ->
                LOGGER.warn("Host {} refused the connection: {}", hostSteamId.accountID, reason)
                instance.execute {
                    instance.gui.hud.chat.addClientSystemMessage(Component.literal("Couldn't join $hostName: $reason"))
                }
            },
        )
    }

    private fun openBridgeToHost(relay: SteamP2PRelay, hostSteamId: SteamID, hostName: String, voicePort: Int) {
        val serverSocket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val localPort = serverSocket.localPort

        if (voicePort != 0) {
            val voiceRelay = p2pVoiceRelay
            if (voiceRelay == null) {
                LOGGER.warn("No voice relay available, skipping voice bridge for {}", hostSteamId.accountID)
            } else {
                try {
                    voiceRelay.start()
                    // A previous attempt's bridge would still hold the port below.
                    voiceRelay.closePeer(hostSteamId)
                    // On an integrated server SVC always sends a blank voice_host, so its client
                    // sends voice to the Minecraft connection's address (this loopback bridge) on
                    // the host's real voice port - the bridge must listen on that exact port.
                    val voiceSocket = DatagramSocket(voicePort, InetAddress.getLoopbackAddress())
                    voiceRelay.addPeer(hostSteamId, voiceSocket)
                } catch (e: IOException) {
                    LOGGER.warn("Failed to set up a voice bridge for {}", hostSteamId.accountID, e)
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

        val voicePort = outPort[0].toInt() and 0xFFFF
        connectToHost(outServer, voicePort)
    }
}
