package xyz.kkaii.steamed.net

// Reads the "enabled" flag and port out of web map configs, as (enabled, port), or null without a port.
internal object WebMapConfig {
    // BlueMap's webserver.conf (HOCON): top-level `enabled: true` and `port: 8100`. Keys inside { } blocks don't count.
    fun readHocon(lines: List<String>): Pair<Boolean, Int>? {
        val values = HashMap<String, String>()
        var depth = 0
        for (raw in lines) {
            val line = raw.substringBefore('#').trim()
            if (depth == 0 && ':' in line) {
                values.putIfAbsent(line.substringBefore(':').trim(), line.substringAfter(':').trim())
            }
            depth = (depth + line.count { it == '{' } - line.count { it == '}' }).coerceAtLeast(0)
        }
        val port = values["port"]?.toIntOrNull() ?: return null
        return (values["enabled"] != "false") to port
    }

    // squaremap's config.yml: `enabled` and `port` under `internal-webserver:`.
    fun readInternalWebserver(lines: List<String>): Pair<Boolean, Int>? {
        val start = lines.indexOfFirst { it.trim() == "internal-webserver:" }
        if (start < 0) return null
        val indent = lines[start].indexOfFirst { !it.isWhitespace() }
        var enabled = true
        var port: Int? = null
        for (line in lines.drop(start + 1)) {
            if (line.isBlank() || line.trim().startsWith("#")) continue
            if (line.indexOfFirst { !it.isWhitespace() } <= indent) break
            val key = line.substringBefore(':').trim()
            val value = line.substringAfter(':').substringBefore('#').trim()
            when (key) {
                "enabled" -> enabled = value != "false"
                "port" -> port = value.toIntOrNull()
            }
        }
        return port?.let { enabled to it }
    }
}
