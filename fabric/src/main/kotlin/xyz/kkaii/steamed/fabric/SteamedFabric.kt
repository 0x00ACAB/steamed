package xyz.kkaii.steamed.fabric

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

class SteamedFabric : ModInitializer, ClientModInitializer {
    private val logger = LoggerFactory.getLogger("steamed")

    override fun onInitialize() {
    }

    override fun onInitializeClient() {
        logger.info("init steamworks client")
    }
}
