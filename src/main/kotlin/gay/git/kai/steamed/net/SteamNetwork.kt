package gay.git.kai.steamed.net

import com.codedisaster.steamworks.SteamID
import com.codedisaster.steamworks.SteamNetworking
import com.codedisaster.steamworks.SteamNetworkingCallback
import gay.git.kai.steamed.Steamed
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetAddress
import java.net.Socket

class SteamNetwork : SteamNetworkingCallback {
    private val logger: Logger = LoggerFactory.getLogger("SteamP2P#PreLaunch")

    override fun onP2PSessionConnectFail(
        steamIDRemote: SteamID?,
        sessionError: SteamNetworking.P2PSessionError?
    ) {
        super.onP2PSessionConnectFail(steamIDRemote, sessionError)
        logger.error("Peer-to-peer connection failure: {}", sessionError)
        steamIDRemote?.let { Steamed.p2pRelay?.closePeer(it) }
    }

    override fun onP2PSessionRequest(steamIDRemote: SteamID) {
        super.onP2PSessionRequest(steamIDRemote)
        logger.info("Peer-to-peer connection request from {}", steamIDRemote.accountID)

        if (!isLobbyMember(steamIDRemote)) {
            logger.warn("Rejecting P2P session request from {}, not a member of the current lobby", steamIDRemote.accountID)
            return
        }

        val port = Steamed.hostedPort
        val relay = Steamed.p2pRelay
        if (port == null || relay == null) {
            logger.warn("Rejecting P2P session request from {}, not currently hosting", steamIDRemote.accountID)
            return
        }

        Steamed.networking?.acceptP2PSessionWithUser(steamIDRemote)
        relay.start()

        try {
            val socket = Socket(InetAddress.getLoopbackAddress(), port)
            relay.addPeer(steamIDRemote, socket)
        } catch (e: IOException) {
            logger.error("Failed to open a local bridge connection for {}", steamIDRemote.accountID, e)
            relay.closePeer(steamIDRemote)
        }
    }

    private fun isLobbyMember(remote: SteamID): Boolean {
        if (!Steamed.lobbyId.isSome()) return false
        val lobby = Steamed.lobbyId.unwrap()
        val matchmaking = Steamed.matchmaking ?: return false
        if (matchmaking.getLobbyOwner(lobby) != Steamed.user?.steamID) return false

        val members = matchmaking.getNumLobbyMembers(lobby)
        for (i in 0 until members) {
            if (matchmaking.getLobbyMemberByIndex(lobby, i) == remote) return true
        }
        return false
    }
}
