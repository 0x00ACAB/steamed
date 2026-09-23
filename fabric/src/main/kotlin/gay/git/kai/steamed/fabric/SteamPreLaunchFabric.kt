package gay.git.kai.steamed.fabric

import gay.git.kai.steamed.SteamInit
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint

class SteamPreLaunchFabric : PreLaunchEntrypoint {
    override fun onPreLaunch() {
        SteamInit.initSteam()
    }
}
