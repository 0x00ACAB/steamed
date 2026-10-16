package xyz.kkaii.steamed.net

import xyz.kkaii.steamed.platform.Services
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

// Finds web map mods whose built-in web server joiners could use through the Steam tunnel.
// Pl3xMap isn't here: its Fabric build only starts on a dedicated server, never in singleplayer.
object WebMapIntegration {
    private val logger: Logger = LoggerFactory.getLogger("WebMapIntegration")

    data class WebMap(val name: String, val port: Int)

    private class Candidate(val name: String, val config: Path, val read: (List<String>) -> Pair<Boolean, Int>?)

    private val gameDir: Path get() = Path.of(Services.PLATFORM.gameDir())

    private val candidates = listOf(
        Candidate("BlueMap", gameDir.resolve("config/bluemap/webserver.conf"), WebMapConfig::readHocon),
        Candidate("squaremap", gameDir.resolve("squaremap/config.yml"), WebMapConfig::readInternalWebserver),
    )

    fun detect(): List<WebMap> = candidates.mapNotNull { candidate ->
        val (enabled, port) = try {
            if (!Files.exists(candidate.config)) return@mapNotNull null
            candidate.read(Files.readAllLines(candidate.config)) ?: return@mapNotNull null
        } catch (e: Exception) {
            logger.warn("Couldn't read {}'s config at {}", candidate.name, candidate.config, e)
            return@mapNotNull null
        }
        if (!enabled || port !in 1..65535) return@mapNotNull null
        // The configured port can belong to something else when the map failed to bind it (8080 is the Steam
        // client's own CEF debugger), so only forward a port that actually serves this map's page.
        if (!servesMap(candidate.name, port)) {
            logger.warn("{} is configured for port {}, but that port doesn't serve {}; not forwarding it", candidate.name, port, candidate.name)
            return@mapNotNull null
        }
        WebMap(candidate.name, port)
    }

    private fun servesMap(name: String, port: Int): Boolean = try {
        val connection = URI("http://127.0.0.1:$port/").toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 500
        connection.readTimeout = 1000
        connection.instanceFollowRedirects = false
        try {
            connection.responseCode == 200 &&
                connection.inputStream.use { String(it.readNBytes(16384)) }.contains(name, ignoreCase = true)
        } finally {
            connection.disconnect()
        }
    } catch (_: Exception) {
        false
    }
}
