package xyz.kkaii.steamed.quilt

import xyz.kkaii.steamed.SteamInit
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint

class SteamPreLaunchQuilt : PreLaunchEntrypoint {
    override fun onPreLaunch() {
        SteamInit.initSteam()
    }
}
