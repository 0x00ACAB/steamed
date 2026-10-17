package xyz.kkaii.steamed

import com.codedisaster.steamworks.SteamFriends
import com.codedisaster.steamworks.SteamID
import com.codedisaster.steamworks.SteamMatchmaking
import com.codedisaster.steamworks.SteamNativeHandle
import net.minecraft.SharedConstants
import xyz.kkaii.steamed.platform.Services
import java.util.concurrent.ConcurrentHashMap

// Steamed lobbies that Steam friends are in, so they can be joined without an invite.
// Steam reports the lobby a friend is in; only lobbies carrying our marker count, since app 480 is shared.
object FriendLobbies {
    const val MARKER_KEY = "steamed"
    const val VISIBILITY_KEY = "visibility"
    const val MC_VERSION_KEY = "mc_version"
    const val MC_PROTOCOL_KEY = "mc_protocol"
    private const val APP_ID = 480L
    private const val REQUEST_INTERVAL_MS = 10_000L

    data class Hosted(
        val lobby: SteamID,
        val friends: List<Pair<SteamID, String>>,
        val world: String,
        val version: String,
        val mcVersion: String,
        val mcProtocol: String,
    ) {
        val sameMinecraft: Boolean get() = sameMinecraft(mcProtocol)
        val compatible: Boolean get() = version == Services.PLATFORM.modVersion() && sameMinecraft
    }

    // Lobbies from before these keys existed don't say; Minecraft's own handshake still catches a mismatch.
    fun sameMinecraft(hostProtocol: String?): Boolean =
        hostProtocol.isNullOrEmpty() || hostProtocol == SharedConstants.getProtocolVersion().toString()

    private val lastRequest = ConcurrentHashMap<Long, Long>()

    fun visibilityOf(type: SteamMatchmaking.LobbyType): String =
        if (type == SteamMatchmaking.LobbyType.Private) "private" else "friends"

    fun scan(): List<Hosted> {
        val friends = Steamed.friends ?: return emptyList()
        val matchmaking = Steamed.matchmaking ?: return emptyList()
        val info = SteamFriends.FriendGameInfo()
        val now = System.currentTimeMillis()
        val byLobby = LinkedHashMap<Long, MutableList<Pair<SteamID, String>>>()
        val lobbies = HashMap<Long, SteamID>()

        for (i in 0 until friends.getFriendCount(SteamFriends.FriendFlags.Immediate)) {
            val friend = friends.getFriendByIndex(i, SteamFriends.FriendFlags.Immediate)
            if (!friends.getFriendGamePlayed(friend, info) || (info.gameID and 0xFFFFFF) != APP_ID) continue
            val lobby = info.steamIDLobby
            if (!lobby.isValid) continue
            val handle = SteamNativeHandle.getNativeHandle(lobby)
            // Lobby data for lobbies we're not in arrives asynchronously after requestLobbyData.
            if (now - (lastRequest[handle] ?: 0L) > REQUEST_INTERVAL_MS) {
                lastRequest[handle] = now
                matchmaking.requestLobbyData(lobby)
            }
            lobbies[handle] = SteamID(lobby)
            byLobby.getOrPut(handle) { mutableListOf() } += SteamID(friend) to friends.getFriendPersonaName(friend)
        }

        return byLobby.mapNotNull { (handle, members) ->
            val lobby = lobbies.getValue(handle)
            val version = matchmaking.getLobbyData(lobby, MARKER_KEY)
            if (version.isNullOrEmpty() || matchmaking.getLobbyData(
                    lobby,
                    VISIBILITY_KEY
                ) == "private"
            ) return@mapNotNull null
            Hosted(
                lobby, members, matchmaking.getLobbyData(lobby, "name").orEmpty(), version,
                matchmaking.getLobbyData(lobby, MC_VERSION_KEY).orEmpty(),
                matchmaking.getLobbyData(lobby, MC_PROTOCOL_KEY).orEmpty(),
            )
        }
    }
}
