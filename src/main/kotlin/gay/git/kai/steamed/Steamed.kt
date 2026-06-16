package gay.git.kai.steamed

import com.codedisaster.steamworks.SteamAPI
import com.codedisaster.steamworks.SteamLibraryLoaderLwjgl3
import gay.git.kai.steamed.tasks.SteamCallbackTask
import net.fabricmc.api.ModInitializer
import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Timer

object Steamed : ModInitializer {
	private val logger = LoggerFactory.getLogger("steamed")
	private val timer = Timer();

	private val steamCallbackTask = SteamCallbackTask()

	override fun onInitialize() {
		if (!createAppid()) return;

		if (!SteamAPI.loadLibraries( SteamLibraryLoaderLwjgl3() )) {
			logger.error("Error whilst initialising steam api (E01): could not load LWJGL3 libraries")
			return
		}

		if (!SteamAPI.init()) {
			logger.error("Error whilst initialising steam api (E02): could not connect to the steam client, is it running?")
			return
		}



		logger.info("Starting steam poller task")
		timer.schedule(steamCallbackTask, 15 / 1000)
	}

	 fun createAppid(): Boolean {
		try {
			File(
				Minecraft.getInstance().gameDirectory.toString() + "/steam_appid.txt"
			).writeText("480")
			return true;
		} catch (e: NullPointerException) {
			logger.error("Error whilst initialising steam api (E01): could not create steam_appid.txt in the game directory")
			logger.error(e.message)
			return false;
		}
	}
}