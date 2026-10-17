package xyz.kkaii.steamed.tasks

import com.codedisaster.steamworks.SteamAPI
import xyz.kkaii.steamed.SteamInit
import java.util.*

class SteamCallbackTask : TimerTask() {
    private var ticks = 0

    override fun run() {
        // Roughly once a second. Steam quitting doesn't arrive as a callback we can rely on.
        if (++ticks % 66 == 0 && !SteamAPI.isSteamRunning(true)) {
            cancel()
            SteamInit.onSteamLost()
            return
        }
        SteamAPI.runCallbacks()
    }
}
