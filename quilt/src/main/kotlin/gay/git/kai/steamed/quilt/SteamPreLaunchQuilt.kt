package gay.git.kai.steamed.quilt

import gay.git.kai.steamed.SteamInit
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint

class SteamPreLaunchQuilt : PreLaunchEntrypoint {
    override fun onPreLaunch() {
        SteamInit.initSteam()
    }
}
