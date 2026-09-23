@file:JvmName("Steamed")
@file:JvmMultifileClass

package gay.git.kai.steamed

import com.codedisaster.steamworks.*
import gay.git.kai.steamed.lib.Option
import gay.git.kai.steamed.net.SteamP2PRelay
import gay.git.kai.steamed.net.SteamP2PVoiceRelay
import gay.git.kai.steamed.net.SvcIntegration
import gay.git.kai.steamed.screen.SteamLobbyScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.client.server.IntegratedServer
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.commands.PublishCommand
import net.minecraft.util.HttpUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.util.*
import kotlin.concurrent.Volatile
import kotlin.concurrent.thread

object Steamed {
    val LOGGER: Logger = LoggerFactory.getLogger("steamp2p")

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

    @Volatile
    var hostedPort: Int? = null

    @Volatile
    var hostedVoicePort: Int? = null

    @Volatile
    var lobbyId: Option<SteamID?> = Option()

    @Volatile
    var lobbies: MutableMap<Long?, SteamID?> = HashMap<Long?, SteamID?>()
    var timer: Timer? = null

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
        val port = HttpUtil.getAvailablePort()

        server.getWorldData().isAllowCommands
        val message = if (server.publishServer(
                MinecraftServer.MultiplayerScope.LAN,
                server.defaultGameType,
                true, port
            )
        ) {
            PublishCommand.getSuccessMessage(port)
        } else {
            Component.translatable("commands.publish.failed")
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
        //todo(kai): auth ticket handshake so a lobby-membership check isn't the only gate (M7)
    }

    fun connectToHost(hostSteamId: SteamID, voicePort: Int = 0) {
        val relay = p2pRelay
        if (relay == null) {
            LOGGER.error("No P2P relay available, cannot connect to {}", hostSteamId.accountID)
            return
        }

        val hostName = friends?.getFriendPersonaName(hostSteamId) ?: "the host"
        LOGGER.info("Connecting to host {} ({}) over Steam P2P", hostName, hostSteamId.accountID)

        val serverSocket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val localPort = serverSocket.localPort

        relay.start()

        if (voicePort != 0) {
            val voiceRelay = p2pVoiceRelay
            if (voiceRelay == null) {
                LOGGER.warn("No voice relay available, skipping voice bridge for {}", hostSteamId.accountID)
            } else {
                try {
                    voiceRelay.start()
                    // Bind on the same port number the host's real voice chat server uses: its
                    // client defaults to "same host as the Minecraft connection" for voice, which
                    // for a joiner is this loopback bridge - so it must listen on that exact port.
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
            instance.gui.hud.chat.addClientSystemMessage(Component.literal("Establishing connection with $hostName..."))
            val address = ServerAddress("127.0.0.1", localPort)
            val serverData = ServerData("Steam lobby", "127.0.0.1:$localPort", ServerData.Type.LAN)
            val parent = instance.gui.screen() ?: SteamLobbyScreen(null)
            ConnectScreen.startConnecting(parent, instance, address, serverData, false, null)
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
