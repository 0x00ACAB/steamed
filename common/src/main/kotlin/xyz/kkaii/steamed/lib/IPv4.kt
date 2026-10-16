package xyz.kkaii.steamed.lib

object IPv4 {
    fun into(ipAddress: String): Int {
        val octets = ipAddress.split(".").dropLastWhile { it.isEmpty() }

        var result = 0
        for (i in octets.indices) {
            val shift = (3 - i) * 8
            result = result or (octets[i].toInt() shl shift)
        }

        return result
    }

    fun from(ipAddress: Int): String {
        return (0..3).joinToString(".") { i ->
            val shift = (3 - i) * 8
            ((ipAddress ushr shift) and 0xFF).toString()
        }
    }
}
