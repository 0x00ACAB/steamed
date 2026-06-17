package gay.git.kai.steamed.lib

import kotlin.math.pow

object IPv4 {
    fun into(ipAddress: String): Int {
        val ipAddressInArray: Array<String?> =
            ipAddress.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        var result = 0
        for (i in ipAddressInArray.indices) {
            val power = 3 - i
            val ip = ipAddressInArray[i]!!.toInt()
            result += (ip * 256.0.pow(power.toDouble())).toInt()
        }

        return result
    }
}
