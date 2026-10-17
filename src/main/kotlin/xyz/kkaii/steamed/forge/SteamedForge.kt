package xyz.kkaii.steamed.forge

import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import org.slf4j.LoggerFactory
import xyz.kkaii.steamed.SteamInit

@Mod("steamed")
class SteamedForge {
    init {
        SteamInit.initSteam()
        FMLJavaModLoadingContext.get().modEventBus.addListener(::onClientSetup)
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        LoggerFactory.getLogger("steamed").info("init steamworks client")
    }
}
