package xyz.kkaii.steamed.forge

import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.loading.FMLPaths
import xyz.kkaii.steamed.platform.IPlatformHelper

class ForgePlatformHelper : IPlatformHelper {
    override fun gameDir(): String = FMLPaths.GAMEDIR.get().toString()

    override fun modVersion(): String =
        ModList.get().getModContainerById("steamed").map { it.modInfo.version.toString() }.orElse("unknown")
}
