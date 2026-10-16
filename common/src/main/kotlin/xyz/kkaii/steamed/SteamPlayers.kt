package xyz.kkaii.steamed

import com.codedisaster.steamworks.SteamID
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import java.util.UUID

// Players who reached the world through Steam P2P, i.e. lobby members other than the host.
// Each member publishes its own Minecraft UUID as lobby member data; nothing checks it against the server.
object SteamPlayers {
    private const val UUID_KEY = "mc_uuid"

    private val ICON: Component = Component.literal("\uE000")
        .withStyle(Style.EMPTY.withFont(FontDescription.Resource(Identifier.fromNamespaceAndPath("steamed", "nametag"))))

    @Volatile
    private var uuids: Set<UUID> = emptySet()

    @JvmStatic
    fun decorate(uuid: UUID, name: Component): Component =
        if (uuid in uuids) Component.empty().append(ICON).append(" ").append(name) else name

    fun publishSelf(lobby: SteamID) {
        Steamed.matchmaking?.setLobbyMemberData(lobby, UUID_KEY, Minecraft.getInstance().user.profileId.toString())
    }

    fun refresh() {
        val matchmaking = Steamed.matchmaking
        if (matchmaking == null || !Steamed.lobbyId.isSome()) {
            uuids = emptySet()
            return
        }
        val lobby = Steamed.lobbyId.unwrap()
        val owner = matchmaking.getLobbyOwner(lobby)
        uuids = (0..<matchmaking.getNumLobbyMembers(lobby))
            .map { matchmaking.getLobbyMemberByIndex(lobby, it) }
            .filter { it != owner }
            .mapNotNull { member ->
                matchmaking.getLobbyMemberData(lobby, member, UUID_KEY)
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            }
            .toSet()
    }

    fun clear() {
        uuids = emptySet()
    }
}
