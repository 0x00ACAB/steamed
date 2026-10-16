package xyz.kkaii.steamed.net

// Mods that open a public tunnel to any published world, which bypasses the loopback bind and the Steam auth gate.
object PublicTunnelMods {
    private val known = mapOf(
        "e4mc" to "link.e4mc.E4mcClient",
        "e4all" to "link.e4all.E4allClient",
    )

    fun installed(): List<String> = known.filterValues { isPresent(it) }.keys.toList()

    private fun isPresent(className: String): Boolean = try {
        Class.forName(className, false, PublicTunnelMods::class.java.classLoader)
        true
    } catch (_: ClassNotFoundException) {
        false
    } catch (_: LinkageError) {
        true
    }
}
