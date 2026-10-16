package xyz.kkaii.steamed.quilt

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

class SteamedQuilt : ModInitializer, ClientModInitializer {
    private val logger = LoggerFactory.getLogger("steamed")

    override fun onInitialize() {
    }

    override fun onInitializeClient() {
        logger.info("init Steamworks client")
    }
}
