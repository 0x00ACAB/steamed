package gay.git.kai.steamed.net

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.Optional

object PlasmoVoiceIntegration {
    private val logger: Logger = LoggerFactory.getLogger("PlasmoVoiceIntegration")

    // With its default config, Plasmo Voice (re)starts its UDP server when the world is published,
    // on the same port number as the Minecraft server, and its clients use the Minecraft connection's
    // address. ModVoiceServer.INSTANCE -> getUdpServer() -> getRemoteAddress() is the bound address.
    fun detectPort(): Int? {
        val modServer = try {
            Class.forName("su.plo.voice.server.ModVoiceServer")
        } catch (_: ClassNotFoundException) {
            return null
        }

        return try {
            val server = modServer.getField("INSTANCE").get(null) ?: return null
            val api = Class.forName("su.plo.voice.api.server.PlasmoVoiceServer")
            val udpApi = Class.forName("su.plo.voice.api.server.socket.UdpServer")

            val udp = (api.getMethod("getUdpServer").invoke(server) as Optional<*>).orElse(null) ?: run {
                logger.info("Plasmo Voice is installed but its UDP server isn't running")
                return null
            }
            val address = (udpApi.getMethod("getRemoteAddress").invoke(udp) as Optional<*>).orElse(null)
                as? InetSocketAddress ?: return null
            address.port.takeIf { it in 1..65535 }
        } catch (e: Exception) {
            logger.warn("Couldn't read Plasmo Voice's UDP server port, its voice won't be tunneled", e)
            null
        }
    }
}
