package gay.git.kai.steamed.net

import com.codedisaster.steamworks.SteamID
import com.codedisaster.steamworks.SteamNetworking
import com.codedisaster.steamworks.SteamNetworkingCallback
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SteamNetwork : SteamNetworkingCallback {
    private val logger: Logger = LoggerFactory.getLogger("SteamP2P#PreLaunch")

    override fun onP2PSessionConnectFail(
        steamIDRemote: SteamID?,
        sessionError: SteamNetworking.P2PSessionError?
    ) {
        super.onP2PSessionConnectFail(steamIDRemote, sessionError)
        logger.error("Peer-to-peer connection failure: ${sessionError.toString()}")
    }

    override fun onP2PSessionRequest(steamIDRemote: SteamID) {
        super.onP2PSessionRequest(steamIDRemote)
        logger.info("Peer-to-peer connection request from $steamIDRemote")
    }
}