package gay.git.kai.steamed.neoforge

import gay.git.kai.steamed.platform.IPlatformHelper
import net.neoforged.fml.loading.FMLPaths

class NeoForgePlatformHelper : IPlatformHelper {
    override fun gameDir(): String = FMLPaths.GAMEDIR.get().toString()
}
