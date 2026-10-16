package gay.git.kai.steamed.net

import org.slf4j.Logger
import org.slf4j.LoggerFactory

object SvcIntegration {
    private val logger: Logger = LoggerFactory.getLogger("SvcIntegration")

    // On an integrated server, SVC ignores its configured port and binds an ephemeral one,
    // so the only reliable source is the running voice server itself:
    // Voicechat.SERVER (ServerVoiceEvents) -> getServer() (Server) -> getPort().
    fun detectPort(): Int? {
        val voicechat = try {
            Class.forName("de.maxhenkel.voicechat.Voicechat")
        } catch (_: ClassNotFoundException) {
            return null
        }

        return try {
            val events = voicechat.getField("SERVER").get(null) ?: return null
            val server = events.javaClass.getMethod("getServer").invoke(events) ?: run {
                logger.info("Simple Voice Chat is installed but its voice server isn't running")
                return null
            }
            val port = server.javaClass.getMethod("getPort").invoke(server) as Int
            if (port in 1..65535) port else null
        } catch (e: Exception) {
            logger.warn("Couldn't read Simple Voice Chat's voice server port, voice won't be tunneled", e)
            null
        }
    }
}
