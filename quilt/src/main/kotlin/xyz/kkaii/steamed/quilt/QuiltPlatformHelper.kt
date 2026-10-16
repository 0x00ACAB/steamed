package xyz.kkaii.steamed.quilt

import org.quiltmc.loader.api.QuiltLoader
import xyz.kkaii.steamed.platform.IPlatformHelper

class QuiltPlatformHelper : IPlatformHelper {
    override fun gameDir(): String = QuiltLoader.getGameDir().toString()

    override fun modVersion(): String =
        QuiltLoader.getModContainer("steamed")
            .map { it.metadata().version().toString() }
            .orElse("unknown") ?: "unknown"

}
