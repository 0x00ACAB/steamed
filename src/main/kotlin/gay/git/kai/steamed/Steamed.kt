@file:JvmName("Steamed")
@file:JvmMultifileClass

package gay.git.kai.steamed

import com.codedisaster.steamworks.*
import gay.git.kai.steamed.lib.Option
import gay.git.kai.steamed.net.SteamP2PRelay
import gay.git.kai.steamed.screen.SteamLobbyScreen
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.ModInitializer
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
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.util.*
import kotlin.concurrent.Volatile
import kotlin.concurrent.thread


class Steamed : ModInitializer, ClientModInitializer {
    private val logger = LoggerFactory.getLogger("steamed")

    override fun onInitialize() {
    }

    override fun onInitializeClient() {
        logger.info("init steamworks client")
    }

    companion object {
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

        @Volatile
        var hostedPort: Int? = null

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

            matchmaking?.setLobbyGameServer(
                lobbyId.unwrap(),
                0,
                0.toShort(),
                user!!.steamID
            )
            mc.gui.hud.chat.addClientSystemMessage(message)
            //todo(kai): auth ticket handshake so a lobby-membership check isn't the only gate (M7)
        }

        fun connectToHost(hostSteamId: SteamID) {
            val relay = p2pRelay
            if (relay == null) {
                LOGGER.error("No P2P relay available, cannot connect to {}", hostSteamId.accountID)
                return
            }

            LOGGER.info("Connecting to host {} over Steam P2P", hostSteamId.accountID)

            val serverSocket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
            val localPort = serverSocket.localPort

            relay.start()
            try {
                val ping = ByteBuffer.allocateDirect(1)
                ping.put(0).flip()
                networking?.sendP2PPacket(hostSteamId, ping, SteamNetworking.P2PSend.Reliable, 0)
            } catch (e: SteamException) {
                LOGGER.error("Failed to open a P2P session with the host", e)
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
                val serverData = ServerData("Steam lobby", "127.0.0.1:$localPort", ServerData.Type.LAN)
                val parent = instance.gui.screen() ?: SteamLobbyScreen(null)
                ConnectScreen.startConnecting(parent, instance, address, serverData, false, null)
            }
        }

        fun joinHostedLobby() {
            val mc: Minecraft = Minecraft.getInstance()
            if (!lobbyId.isSome()) {
                mc.gui.hud.chat.addClientSystemMessage(Component.literal("You're not in a lobby"))
                return
            }

            val outIp = IntArray(1)
            val outPort = ShortArray(1)
            val outServer = SteamID()
            val hasServer = matchmaking?.getLobbyGameServer(lobbyId.unwrap(), outIp, outPort, outServer) ?: false

            if (!hasServer) {
                LOGGER.error("Lobby has no game server set yet")
                mc.gui.hud.chat.addClientSystemMessage(Component.literal("The host hasn't started their world yet"))
                return
            }

            if (outServer == user?.steamID) {
                mc.gui.hud.chat.addClientSystemMessage(Component.literal("You're the host"))
                return
            }

            connectToHost(outServer)
        }
    }
}