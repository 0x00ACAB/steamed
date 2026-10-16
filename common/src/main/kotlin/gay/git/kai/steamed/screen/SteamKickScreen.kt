package gay.git.kai.steamed.screen

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

class SteamKickScreen(private val lastScreen: Screen?) : Screen(Component.literal("Kick a Player")) {
    private val layout: HeaderAndFooterLayout = HeaderAndFooterLayout(this, 33, 60)
    private var memberList: MemberSelectionList? = null

    override fun init() {
        this.layout.addTitleHeader(this.title, this.font)

        this.memberList = this.layout.addToContents(MemberSelectionList(this.minecraft))

        val footer: LinearLayout = this.layout.addToFooter(LinearLayout.horizontal().spacing(4))
        footer.addChild(Button.builder(Component.literal("Kick"), { _ ->
            val entry = this.memberList?.selected
            if (entry == null || entry.kicked) return@builder

            Steamed.kick(entry.steamId)
            entry.markKicked()
            LOGGER.info("Kicked {} from the lobby", entry.name)
        }).width(150).build())

        footer.addChild(Button.builder(CommonComponents.GUI_BACK, { _ -> this.onClose() }).width(150).build())

        this.layout.visitWidgets(this::addRenderableWidget)
        this.repositionElements()
    }

    override fun repositionElements() {
        this.layout.arrangeElements()
        this.memberList?.updateSize(this.width, this.layout)
    }

    override fun onClose() {
        this.minecraft.gui.setScreen(this.lastScreen)
    }

    private inner class MemberSelectionList(minecraft: Minecraft) : ObjectSelectionList<MemberSelectionList.Entry>(
        minecraft, this@SteamKickScreen.width, this@SteamKickScreen.height - 33 - 60, 33, 20
    ) {
        init {
            val matchmaking = Steamed.matchmaking
            if (matchmaking == null || !Steamed.lobbyId.isSome()) {
                LOGGER.error("Not in a lobby, cannot list members")
            } else {
                val lobby = Steamed.lobbyId.unwrap()
                val self = Steamed.user?.steamID
                for (i in 0 until matchmaking.getNumLobbyMembers(lobby)) {
                    val id = matchmaking.getLobbyMemberByIndex(lobby, i)
                    if (id == self) continue

                    val name = Steamed.friends?.getFriendPersonaName(id) ?: "unknown"
                    this.addEntry(Entry(id, name, id in Steamed.kickedPeers))
                }
            }
        }

        inner class Entry(val steamId: SteamID, val name: String, kicked: Boolean) :
            ObjectSelectionList.Entry<Entry>() {
            var kicked = kicked
                private set

            fun markKicked() {
                kicked = true
            }

            override fun extractContent(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovered: Boolean, a: Float) {
                val label = if (kicked) "$name (kicked)" else name
                graphics.centeredText(
                    this@SteamKickScreen.font,
                    Component.literal(label),
                    this@MemberSelectionList.width / 2,
                    this.contentYMiddle - 9 / 2,
                    -1
                )
            }

            override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
                if (!kicked) this@MemberSelectionList.selected = this
                return super.mouseClicked(event, doubleClick)
            }

            override fun getNarration(): Component = Component.literal(name)
        }
    }

    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
    }
}
