package gay.git.kai.steamed.lib

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
}
