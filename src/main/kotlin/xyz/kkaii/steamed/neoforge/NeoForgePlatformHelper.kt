package xyz.kkaii.steamed.neoforge

import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLPaths
import xyz.kkaii.steamed.platform.IPlatformHelper

class NeoForgePlatformHelper : IPlatformHelper {
    override fun gameDir(): String = FMLPaths.GAMEDIR.get().toString()

    override fun modVersion(): String =
        ModList.get().getModContainerById("steamed").map { it.modInfo.version.toString() }.orElse("unknown")
}
