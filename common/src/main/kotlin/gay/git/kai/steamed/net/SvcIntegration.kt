package gay.git.kai.steamed.net

import gay.git.kai.steamed.platform.Services
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Properties

object SvcIntegration {
    private val logger: Logger = LoggerFactory.getLogger("SvcIntegration")
    private const val DEFAULT_PORT = 24454

    fun detectPort(): Int? {
        val file = File(Services.PLATFORM.gameDir(), "config/voicechat/voicechat-server.properties")
        if (!file.exists()) return null

        return try {
            val props = Properties()
            file.inputStream().use { props.load(it) }
            props.getProperty("port")?.trim()?.toIntOrNull() ?: DEFAULT_PORT
        } catch (e: Exception) {
            logger.warn("Failed to read Simple Voice Chat's config, assuming it isn't installed", e)
            null
        }
    }
}
