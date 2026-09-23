package gay.git.kai.steamed.quilt

import gay.git.kai.steamed.platform.IPlatformHelper
import net.fabricmc.loader.api.FabricLoader

class QuiltPlatformHelper : IPlatformHelper {
    override fun gameDir(): String = FabricLoader.getInstance().gameDir.toString()
}
