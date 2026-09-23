package gay.git.kai.steamed.neoforge

import gay.git.kai.steamed.SteamInit
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import org.slf4j.LoggerFactory

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
