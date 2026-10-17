package xyz.kkaii.steamed.net

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

// The web relay's map list: a count byte, then each map name as a length byte and UTF-8 bytes.
internal object WebMapList {
    fun encode(type: Byte, names: List<String>): ByteArray {
        require(names.size <= 255) { "too many maps: ${names.size}" }
        val encoded = names.map { name ->
            name.toByteArray(StandardCharsets.UTF_8).also { require(it.size <= 255) { "map name too long: $name" } }
        }
        val frame = ByteBuffer.allocate(2 + encoded.sumOf { 1 + it.size })
        frame.put(type).put(names.size.toByte())
        encoded.forEach { frame.put(it.size.toByte()).put(it) }
        return frame.array()
    }

    // Reads the names after the type byte, which the caller has already consumed.
    fun decode(frame: ByteBuffer): List<String> = (0 until (frame.get().toInt() and 0xFF)).map {
        val bytes = ByteArray(frame.get().toInt() and 0xFF)
        frame.get(bytes)
        String(bytes, StandardCharsets.UTF_8)
    }
}
