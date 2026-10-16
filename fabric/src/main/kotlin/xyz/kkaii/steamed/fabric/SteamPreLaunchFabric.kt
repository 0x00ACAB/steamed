package xyz.kkaii.steamed.fabric

import xyz.kkaii.steamed.SteamInit
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint

class SteamPreLaunchFabric : PreLaunchEntrypoint {
    override fun onPreLaunch() {
        SteamInit.initSteam()
    }
}
