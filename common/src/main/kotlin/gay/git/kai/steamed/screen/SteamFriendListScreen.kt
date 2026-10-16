package gay.git.kai.steamed.screen

import com.codedisaster.steamworks.SteamFriends
import com.codedisaster.steamworks.SteamID
import com.mojang.logging.LogUtils
import gay.git.kai.steamed.Steamed
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import org.slf4j.Logger

class SteamFriendListScreen(private val lastScreen: Screen?) : Screen(Component.literal("Invite a Friend")) {
    private val layout: HeaderAndFooterLayout = HeaderAndFooterLayout(this, 33, 60)
    private var friendList: FriendSelectionList? = null

    override fun init() {
        this.layout.addTitleHeader(this.title, this.font)

        this.friendList = this.layout.addToContents(FriendSelectionList(this.minecraft))

        val footer: LinearLayout = this.layout.addToFooter(LinearLayout.horizontal().spacing(4))
        footer.addChild(Button.builder(Component.literal("Invite"), { _ ->
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

    override fun repositionElements() {
        this.layout.arrangeElements()
        this.friendList?.updateSize(this.width, this.layout)
    }

    override fun onClose() {
        this.minecraft.gui.setScreen(this.lastScreen)
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

                    this.addEntry(Entry(id, friends.getFriendPersonaName(id), state))
                }
            }
        }

        inner class Entry(val steamId: SteamID, val name: String, private val state: SteamFriends.PersonaState) :
            ObjectSelectionList.Entry<Entry>() {
            var invited = false
                private set

            fun markInvited() {
                invited = true
            }

            override fun extractContent(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovered: Boolean, a: Float) {
                val label = if (invited) "$name (invited)" else "$name - $state"
                graphics.centeredText(
                    this@SteamFriendListScreen.font,
                    Component.literal(label),
                    this@FriendSelectionList.width / 2,
                    this.contentYMiddle - 9 / 2,
                    -1
                )
            }

            override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
                if (!invited) this@FriendSelectionList.selected = this
                return super.mouseClicked(event, doubleClick)
            }

            override fun getNarration(): Component = Component.literal(name)
        }
    }

    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
    }
}
