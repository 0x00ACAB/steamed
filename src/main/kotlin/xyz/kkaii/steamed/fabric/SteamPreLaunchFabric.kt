package xyz.kkaii.steamed.fabric

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
import xyz.kkaii.steamed.SteamInit

class SteamPreLaunchFabric : PreLaunchEntrypoint {
    override fun onPreLaunch() {
        SteamInit.initSteam()
    }
}
