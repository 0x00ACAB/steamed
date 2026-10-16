package gay.git.kai.steamed.net

import com.codedisaster.steamworks.SteamID
import com.codedisaster.steamworks.SteamNetworking
import com.codedisaster.steamworks.SteamNetworkingCallback
import gay.git.kai.steamed.Steamed
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.DatagramSocket
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
        steamIDRemote?.let {
            Steamed.authHandshake?.endPeer(it)
            unbridgePeer(it)
        }
    }

    override fun onP2PSessionRequest(steamIDRemote: SteamID) {
        super.onP2PSessionRequest(steamIDRemote)
        logger.info("Peer-to-peer connection request from {}", steamIDRemote.accountID)

        refusalReason(steamIDRemote)?.let {
            logger.warn("Rejecting P2P session request from {}: {}", steamIDRemote.accountID, it)
            return
        }

        // Accepting only lets the peer reach SteamAuthHandshake; nothing is bridged until its ticket validates.
        Steamed.networking?.acceptP2PSessionWithUser(steamIDRemote)
        Steamed.p2pRelay?.start()
        Steamed.authHandshake?.start()
    }

    fun refusalReason(remote: SteamID): String? = when {
        remote in Steamed.kickedPeers -> "You were kicked from this lobby"
        !isLobbyMember(remote) -> "You're not in the host's lobby"
        Steamed.hostedPort == null || Steamed.p2pRelay == null -> "The host isn't hosting a world"
        else -> null
    }

    fun bridgePeer(peer: SteamID): Boolean {
        val port = Steamed.hostedPort ?: return false
        val relay = Steamed.p2pRelay ?: return false

        try {
            val socket = Socket(InetAddress.getLoopbackAddress(), port)
            relay.addPeer(peer, socket)
        } catch (e: IOException) {
            logger.error("Failed to open a local bridge connection for {}", peer.accountID, e)
            relay.closePeer(peer)
            return false
        }

        val voicePort = Steamed.hostedVoicePort
        val voiceRelay = Steamed.p2pVoiceRelay
        if (voicePort != null && voicePort != 0 && voiceRelay != null) {
            try {
                voiceRelay.start()
                val voiceSocket = DatagramSocket()
                voiceSocket.connect(InetAddress.getLoopbackAddress(), voicePort)
                voiceRelay.addPeer(peer, voiceSocket)
            } catch (e: IOException) {
                logger.warn("Failed to open a local voice bridge for {}", peer.accountID, e)
                voiceRelay.closePeer(peer)
            }
        }
        return true
    }

    fun unbridgePeer(peer: SteamID) {
        Steamed.p2pVoiceRelay?.closePeer(peer)
        Steamed.p2pRelay?.closePeer(peer)
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
