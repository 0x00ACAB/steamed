package xyz.kkaii.steamed.quilt

import xyz.kkaii.steamed.platform.IPlatformHelper
import net.fabricmc.loader.api.FabricLoader
import org.quiltmc.loader.api.QuiltLoader

class QuiltPlatformHelper : IPlatformHelper {
    override fun gameDir(): String = QuiltLoader.getGameDir().toString()

    override fun modVersion(): String =
        QuiltLoader.getModContainer("steamed")
            .map { it.metadata().version().toString() }
            .orElse("unknown") ?: "unknown"

}
