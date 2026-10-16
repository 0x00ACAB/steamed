package xyz.kkaii.steamed.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebMapConfigTest {
    // Trimmed from a webserver.conf BlueMap 5.29 generated.
    private val blueMap = """
        # Enable the integrated web server
        enabled: true
        webroot: "bluemap/web"
        port: 8100 # the web server port
        additional-headers: {
          "Cache-Control": "public, max-age=86400"
        }
        log: {
          file: "bluemap/logs/webserver.log"
          append: false
        }
    """.trimIndent().lines()

    // Trimmed from a config.yml squaremap 1.3.15 generated.
    private val squaremap = """
        config-version: 2
        settings:
            web-address: http://localhost:8080
            image-quality:
                compress-images:
                    enabled: false
            internal-webserver:
                enabled: true
                bind: 0.0.0.0
                port: 8081
                flush-json-immediately: false
            ui:
                coordinates:
                    enabled: true
    """.trimIndent().lines()

    @Test
    fun `reads BlueMap's port`() = assertEquals(true to 8100, WebMapConfig.readHocon(blueMap))

    @Test
    fun `BlueMap can be disabled`() =
        assertEquals(false to 8100, WebMapConfig.readHocon(blueMap.map { it.replace("enabled: true", "enabled: false") }))

    @Test
    fun `BlueMap keys inside blocks are ignored`() {
        val nested = listOf("log: {", "  port: 1234", "  enabled: false", "}", "port: 8100")
        assertEquals(true to 8100, WebMapConfig.readHocon(nested))
    }

    @Test
    fun `BlueMap without a port`() = assertNull(WebMapConfig.readHocon(listOf("enabled: true")))

    @Test
    fun `reads squaremap's internal web server`() = assertEquals(true to 8081, WebMapConfig.readInternalWebserver(squaremap))

    @Test
    fun `squaremap's other enabled flags don't leak in`() {
        val disabled = squaremap.map { if (it.trim() == "enabled: true" && it.startsWith("        ") && !it.startsWith("            ")) it.replace("true", "false") else it }
        assertEquals(false to 8081, WebMapConfig.readInternalWebserver(disabled))
    }

    @Test
    fun `squaremap without the section`() = assertNull(WebMapConfig.readInternalWebserver(listOf("settings:", "    ui: {}")))

    @Test
    fun `squaremap port with a comment`() =
        assertEquals(true to 9000, WebMapConfig.readInternalWebserver(listOf("internal-webserver:", "  port: 9000 # custom")))
}
