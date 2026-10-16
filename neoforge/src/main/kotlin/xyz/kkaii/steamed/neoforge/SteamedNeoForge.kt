package xyz.kkaii.steamed.neoforge

import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import org.slf4j.LoggerFactory
import xyz.kkaii.steamed.SteamInit

@Mod("steamed")
class SteamedNeoForge(modEventBus: IEventBus) {
    init {
        SteamInit.initSteam()
        modEventBus.addListener(::onClientSetup)
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        LoggerFactory.getLogger("steamed").info("init steamworks client")
    }
}
