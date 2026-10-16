package xyz.kkaii.steamed.quilt

import xyz.kkaii.steamed.platform.IPlatformHelper
import net.fabricmc.loader.api.FabricLoader

class QuiltPlatformHelper : IPlatformHelper {
    override fun gameDir(): String = FabricLoader.getInstance().gameDir.toString()
}
