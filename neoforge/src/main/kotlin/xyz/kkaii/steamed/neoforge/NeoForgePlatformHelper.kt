package xyz.kkaii.steamed.neoforge

import xyz.kkaii.steamed.platform.IPlatformHelper
import net.neoforged.fml.loading.FMLPaths

class NeoForgePlatformHelper : IPlatformHelper {
    override fun gameDir(): String = FMLPaths.GAMEDIR.get().toString()
}
