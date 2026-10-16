package xyz.kkaii.steamed

import com.codedisaster.steamworks.SteamID
import com.codedisaster.steamworks.SteamNativeHandle
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.contents.objects.PlayerSprite
import net.minecraft.resources.Identifier
import net.minecraft.world.item.component.ResolvableProfile
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import xyz.kkaii.steamed.platform.Services
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap

// Minecraft UUIDs of Steam users, for the nametag marker and for heads in the lobby and invite screens.
// Every Steamed player publishes its own UUID as lobby member data and as Steam rich presence (visible to
// friends running Steamed). UUIDs seen either way are remembered on disk, so friends who aren't online in
// Steamed right now still get a head. All of it is self-reported and only cosmetic.
object SteamPlayers {
    private const val UUID_KEY = "mc_uuid"
    private val LOGGER: Logger = LoggerFactory.getLogger("SteamPlayers")

    private val knownFile: Path by lazy {
        Path.of(
            Services.PLATFORM.gameDir(),
            "config",
            "steamed-known-players.properties"
        )
    }
    private val known: ConcurrentHashMap<Long, UUID> by lazy { load() }

    private val ICON: Component = Component.literal("\uE000")
        .withStyle(
            Style.EMPTY.withFont(
                FontDescription.Resource(
                    Identifier.fromNamespaceAndPath(
                        "steamed",
                        "nametag"
                    )
                )
            )
        )

    @JvmStatic
    fun decorate(uuid: UUID, name: Component): Component =
        if (uuid in uuids) Component.empty().append(ICON).append(" ").append(name) else name

    // Players who reached the world through Steam P2P, i.e. lobby members other than the host.
    @Volatile
    private var uuids: Set<UUID> = emptySet()

    fun publishSelf(lobby: SteamID) {
        Steamed.matchmaking?.setLobbyMemberData(lobby, UUID_KEY, selfUuid())
    }

    fun publishPresence() {
        Steamed.friends?.setRichPresence(UUID_KEY, selfUuid())
    }

    private fun selfUuid(): String = Minecraft.getInstance().user.profileId.toString()

    fun refresh() {
        val matchmaking = Steamed.matchmaking
        if (matchmaking == null || !Steamed.lobbyId.isSome()) {
            uuids = emptySet()
            return
        }
        val lobby = Steamed.lobbyId.unwrap() ?: return
        val owner = matchmaking.getLobbyOwner(lobby)
        uuids = (0..<matchmaking.getNumLobbyMembers(lobby))
            .map { matchmaking.getLobbyMemberByIndex(lobby, it) }
            .filter { it != owner }
            .mapNotNull { memberUuid(lobby, it) }
            .toSet()
    }

    fun memberUuid(lobby: SteamID, member: SteamID): UUID? =
        parse(Steamed.matchmaking?.getLobbyMemberData(lobby, member, UUID_KEY))?.also { remember(member, it) }

    // Rich presence first (current), then what was seen before.
    fun friendUuid(friend: SteamID): UUID? =
        parse(Steamed.friends?.getFriendRichPresence(friend, UUID_KEY))?.also { remember(friend, it) }
            ?: known[SteamNativeHandle.getNativeHandle(friend)]

    private fun parse(value: String?): UUID? =
        value?.takeIf { it.isNotEmpty() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private fun remember(steamId: SteamID, uuid: UUID) {
        if (known.put(SteamNativeHandle.getNativeHandle(steamId), uuid) != uuid) save()
    }

    private fun load(): ConcurrentHashMap<Long, UUID> {
        val map = ConcurrentHashMap<Long, UUID>()
        try {
            if (Files.exists(knownFile)) {
                val props = Properties()
                Files.newBufferedReader(knownFile).use { props.load(it) }
                for (key in props.stringPropertyNames()) {
                    val id = key.toLongOrNull() ?: continue
                    parse(props.getProperty(key))?.let { map[id] = it }
                }
            }
        } catch (e: Exception) {
            LOGGER.warn("Couldn't read {}", knownFile, e)
        }
        return map
    }

    @Synchronized
    private fun save() {
        try {
            val props = Properties()
            known.forEach { (id, uuid) -> props.setProperty(id.toString(), uuid.toString()) }
            Files.createDirectories(knownFile.parent)
            Files.newBufferedWriter(knownFile).use { props.store(it, "Steam ID to Minecraft UUID, for player heads") }
        } catch (e: Exception) {
            LOGGER.warn("Couldn't write {}", knownFile, e)
        }
    }

    fun head(uuid: UUID): Component = Component.`object`(PlayerSprite(ResolvableProfile.createUnresolved(uuid), true))

    fun clear() {
        uuids = emptySet()
    }
}
