package xyz.kkaii.steamed.quilt

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
import xyz.kkaii.steamed.SteamInit

class SteamPreLaunchQuilt : PreLaunchEntrypoint {
    override fun onPreLaunch() {
        SteamInit.initSteam()
    }
}
