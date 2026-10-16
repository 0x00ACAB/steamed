package xyz.kkaii.steamed.tasks

import com.codedisaster.steamworks.SteamFriendsCallback
import com.codedisaster.steamworks.SteamID
import xyz.kkaii.steamed.Steamed

class SteamFriendsCallbackEx : SteamFriendsCallback {
    // "Join Game" from the Steam friends list while Minecraft is already running.
    override fun onGameLobbyJoinRequested(steamIDLobby: SteamID, steamIDFriend: SteamID) {
        Steamed.LOGGER.info("Join requested from the Steam friends list for lobby {} (friend {})", steamIDLobby, steamIDFriend.accountID)
        Steamed.joinLobby(steamIDLobby)
    }
}
