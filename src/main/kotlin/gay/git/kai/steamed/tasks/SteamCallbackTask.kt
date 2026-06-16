package gay.git.kai.steamed.tasks

import com.codedisaster.steamworks.SteamAPI
import java.util.TimerTask

class SteamCallbackTask : TimerTask() {
    override fun run() {
        SteamAPI.runCallbacks();
    }
}