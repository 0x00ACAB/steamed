package xyz.kkaii.steamed.screen

import com.codedisaster.steamworks.SteamFriends
import com.codedisaster.steamworks.SteamID
import com.mojang.logging.LogUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import org.slf4j.Logger
import xyz.kkaii.steamed.SteamPlayers
import xyz.kkaii.steamed.Steamed
import xyz.kkaii.steamed.compat.Graphics
import xyz.kkaii.steamed.compat.SelectionRow
import xyz.kkaii.steamed.compat.closeTo
import xyz.kkaii.steamed.compat.face
import java.util.UUID

class SteamFriendListScreen(private val lastScreen: Screen?) : Screen(Component.translatable("steamed.invite_screen.title")) {
    private val layout: HeaderAndFooterLayout = HeaderAndFooterLayout(this, 33, 60)
    private var friendList: FriendSelectionList? = null

    override fun init() {
        this.layout.addTitleHeader(this.title, this.font)

        this.friendList = this.layout.addToContents(FriendSelectionList(this.minecraft))

        val footer: LinearLayout = this.layout.addToFooter(LinearLayout.horizontal().spacing(4))
        footer.addChild(Button.builder(Component.translatable("steamed.invite_screen.invite"), { _ ->
            val entry = this.friendList?.selected
            if (entry == null || entry.invited) return@builder

            val invited = Steamed.matchmaking?.inviteUserToLobby(Steamed.lobbyId.unwrap(), entry.steamId) ?: false
            if (invited) {
                Steamed.unkick(entry.steamId)
                entry.markInvited()
                LOGGER.info("Invited {} to the lobby", entry.name)
            } else {
                LOGGER.error("Failed to invite {} to the lobby", entry.name)
            }
        }).width(150).build())

        footer.addChild(Button.builder(CommonComponents.GUI_BACK, { _ -> this.onClose() }).width(150).build())

        this.layout.visitWidgets(this::addRenderableWidget)
        this.repositionElements()
    }

    private var ticks = 0

    // Rich presence for friends arrives asynchronously after requestFriendRichPresence.
    override fun tick() {
        super.tick()
        if (++ticks % 20 == 0) this.friendList?.children()?.forEach { it.resolveHead() }
    }

    override fun repositionElements() {
        this.layout.arrangeElements()
        this.friendList?.updateSize(this.width, this.layout)
    }

    override fun onClose() {
        this.minecraft.closeTo(this.lastScreen)
    }

    private inner class FriendSelectionList(minecraft: Minecraft) : ObjectSelectionList<FriendSelectionList.Entry>(
        minecraft, this@SteamFriendListScreen.width, this@SteamFriendListScreen.height - 33 - 60, 33, 20
    ) {
        init {
            val friends = Steamed.friends
            if (friends == null) {
                LOGGER.error("Steamed.friends is null, cannot list friends")
            } else {
                val count = friends.getFriendCount(SteamFriends.FriendFlags.Immediate)
                for (i in 0 until count) {
                    val id = friends.getFriendByIndex(i, SteamFriends.FriendFlags.Immediate)
                    val state = friends.getFriendPersonaState(id)
                    if (state == SteamFriends.PersonaState.Offline) continue

                    friends.requestFriendRichPresence(id)
                    this.addEntry(Entry(id, friends.getFriendPersonaName(id), state))
                }
            }
        }

        inner class Entry(val steamId: SteamID, val name: String, private val state: SteamFriends.PersonaState) :
            SelectionRow<Entry>() {
            var invited = false
                private set

            private var uuid: UUID? = null

            init {
                resolveHead()
            }

            fun resolveHead() {
                if (uuid == null) uuid = SteamPlayers.friendUuid(steamId)
            }

            fun markInvited() {
                invited = true
            }

            override fun draw(graphics: Graphics, x: Int, y: Int, width: Int, height: Int, mouseX: Int, mouseY: Int, a: Float) {
                val font = this@SteamFriendListScreen.font
                val label = if (invited) "$name (invited)" else "$name - $state"
                val face = uuid
                val total = font.width(label) + if (face != null) 10 else 0
                var left = this@FriendSelectionList.width / 2 - total / 2
                val top = y + (height - 9) / 2
                if (face != null) {
                    graphics.face(face, left, top, 8)
                    left += 10
                }
                graphics.text(font, label, left, top, -1)
            }

            override fun onClick() {
                if (!invited) this@FriendSelectionList.selected = this
            }

            override fun getNarration(): Component = Component.literal(name)
        }
    }

    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
    }
}
