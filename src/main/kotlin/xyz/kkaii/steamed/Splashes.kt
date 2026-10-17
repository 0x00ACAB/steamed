package xyz.kkaii.steamed

import com.codedisaster.steamworks.SteamFriends
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import java.util.*
import kotlin.random.Random

// Title screen splashes about Steam and P2P. The plain ones join vanilla's pool; now and then a personal one,
// built from the Steam friends list, replaces the pick.
object Splashes {
    private val STYLE: Style = Style.EMPTY.withColor(-256)
    private const val PERSONAL_CHANCE = 10

    private val LINES = listOf(
        "Full steam ahead!",
        "Freshly steamed!",
        "Now with 100% more steam!",
        "No port forwarding!",
        "NAT traversal!",
        "Peer to peer to peer!",
        "Playing Spacewar!",
        "App ID 480!",
        "Let off some steam!",
        "Steamed hams!",
        "Relayed through Steam!",
        "UPnP not required!",
        "Tea included!",
        "Look for the orange diamond!",
        "Invites ask first!",
    )

    @JvmStatic
    fun extraSplashes(): List<Component> = LINES.map { Component.literal(it).setStyle(STYLE) }

    // Null when there's nothing personal to say, or this time it's vanilla's turn.
    @JvmStatic
    fun personalSplash(): Component? {
        if (Random.nextInt(PERSONAL_CHANCE) != 0) return null
        val friends = Steamed.friends ?: return null
        val options = mutableListOf<String>()
        friends.personaName?.takeIf { it.isNotBlank() }?.let { options += "${it.uppercase(Locale.ROOT)} IS STEAMED" }
        FriendLobbies.scan().firstOrNull()?.let { options += "${it.friends.first().second} is hosting!" }
        val online = (0 until friends.getFriendCount(SteamFriends.FriendFlags.Immediate))
            .count {
                friends.getFriendPersonaState(
                    friends.getFriendByIndex(
                        it,
                        SteamFriends.FriendFlags.Immediate
                    )
                ) != SteamFriends.PersonaState.Offline
            }
        if (online > 1) options += "$online Steam friends online!"
        return options.randomOrNull()?.let { Component.literal(it).setStyle(STYLE) }
    }
}
