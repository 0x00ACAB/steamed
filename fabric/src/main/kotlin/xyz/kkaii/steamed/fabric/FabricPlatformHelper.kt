package xyz.kkaii.steamed.fabric

import xyz.kkaii.steamed.platform.IPlatformHelper
import net.fabricmc.loader.api.FabricLoader

class FabricPlatformHelper : IPlatformHelper {
    override fun gameDir(): String = FabricLoader.getInstance().gameDir.toString()

    override fun modVersion(): String =
        FabricLoader.getInstance().getModContainer("steamed").map { it.metadata.version.friendlyString }.orElse("unknown")
}
