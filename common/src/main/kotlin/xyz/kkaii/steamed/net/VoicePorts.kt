package xyz.kkaii.steamed.net

import java.nio.charset.StandardCharsets

// The handshake's accepted reply: the voice ports the host bridged for this joiner, comma-separated.
internal object VoicePorts {
    fun encode(ports: List<Int>): ByteArray = ports.joinToString(",").toByteArray(StandardCharsets.US_ASCII)

    fun decode(payload: ByteArray): List<Int> =
        String(payload, StandardCharsets.US_ASCII).split(',').mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..65535 }
}
