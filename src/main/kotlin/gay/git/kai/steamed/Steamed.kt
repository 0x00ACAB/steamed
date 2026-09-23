@file:JvmName("Steamed")
@file:JvmMultifileClass

package gay.git.kai.steamed

import com.codedisaster.steamworks.*
import gay.git.kai.steamed.lib.IPv4
import gay.git.kai.steamed.lib.Option
import gay.git.kai.steamed.net.UPnPublisher
import kotlinx.io.IOException
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.ModInitializer
import net.minecraft.client.Minecraft
import net.minecraft.client.server.IntegratedServer
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.commands.PublishCommand
import net.minecraft.util.HttpUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xml.sax.SAXException
import java.util.*
import javax.xml.parsers.ParserConfigurationException
import kotlin.concurrent.Volatile


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


            val published: Option<String?>
            try {
                published = UPnPublisher().publish(port)
            } catch (e: IOException) {
                LOGGER.error(e.localizedMessage)
                return
            } catch (e: ParserConfigurationException) {
                LOGGER.error(e.localizedMessage)
                return
            } catch (e: SAXException) {
                LOGGER.error(e.localizedMessage)
                return
            }

            if (!published.isSome()) {
                LOGGER.error("Could not forward port {}; friends likely won't be able to connect", port)
                mc.gui.hud.chat.addClientSystemMessage(
                    Component.literal("Could not forward the port through UPnP — check your router's UPnP settings, friends may not be able to connect")
                )
            }
            val ip = published.unwrapOr("0.0.0.0")!!

            matchmaking?.setLobbyGameServer(
                lobbyId.unwrap(),
                IPv4.into(ip),
                (port.toShort()),
                SteamID()
            )
            mc.gui.hud.chat.addClientSystemMessage(message)
            //todo(kai): https://partner.steamgames.com/doc/api/ISteamNetworking#SendP2PPacket
            //todo(kai): whitelist steam access tokens (possibly pre-join handshake packet with an auth token + lobby id)
        }
    }
}