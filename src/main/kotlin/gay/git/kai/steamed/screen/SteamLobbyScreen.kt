package gay.git.kai.steamed.screen

import com.codedisaster.steamworks.SteamMatchmaking
import com.mojang.blaze3d.platform.InputConstants.KEY_F5
import com.mojang.logging.LogUtils
import gay.git.kai.steamed.Steamed
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import org.slf4j.Logger

class SteamLobbyScreen(private val lastScreen: Screen?) : Screen(Component.literal("Lobby")) {
    private val layout: HeaderAndFooterLayout = HeaderAndFooterLayout(this, 33, 60)
    private var kickButton: Button? = null
    private var inviteButton: Button? = null
    private var createLobby: Button? = null
    private var joinButton: Button? = null

    private var isInsideLobby = false
    private var isOwner = false

    private var lobbyMembers: ArrayList<String> = ArrayList()

    init {
        this.isInsideLobby = Steamed.lobbyId.isSome()
        if (this.isInsideLobby) this.isOwner =
            Steamed.matchmaking?.getLobbyOwner(Steamed.lobbyId.unwrap()) == Steamed.user?.steamID
    }

    override fun init() {
        this.layout.addTitleHeader(this.title, this.font)
        val footer: LinearLayout = this.layout.addToFooter(LinearLayout.vertical().spacing(4))
        footer.defaultCellSetting().alignHorizontallyCenter()
        val topFooterButtons: LinearLayout = footer.addChild(LinearLayout.horizontal().spacing(4))
        val bottomFooterButtons: LinearLayout = footer.addChild(LinearLayout.horizontal().spacing(4))

        if (!this.isInsideLobby) {
            this.createLobby = topFooterButtons.addChild(Button.builder(Component.literal("Create Lobby"), { self ->
                Steamed.matchmaking?.createLobby(SteamMatchmaking.LobbyType.FriendsOnly, 20)?.isValid?.let {
                    if (!it) {
                        LOGGER.error("Failed to create lobby")
                        return@builder
                    }
                }
                self.active = false
            }).width(230).build())

            if (!this.minecraft.hasSingleplayerServer()) {
                disableButtons("Load a world first to host a lobby", this.createLobby)
            }
        } else if (!this.isOwner) {
            this.joinButton = topFooterButtons.addChild(Button.builder(Component.literal("Join Host"), { `_` ->
                Steamed.joinHostedLobby()
            }).width(230).build())
        }

        this.inviteButton = bottomFooterButtons.addChild(Button.builder(Component.literal("Invite"), { `_` ->
            this.minecraft.setScreenAndShow(SteamFriendListScreen(this))
        }).width(74).build())

        this.kickButton =
            bottomFooterButtons.addChild(Button.builder(Component.literal("Kick"), { `_` -> }).width(74).build())

        disableButtons("You are not in a lobby!", this.inviteButton, this.kickButton)

        if (this.isInsideLobby) {
            enableButtons(this.inviteButton, this.kickButton)

            if (!this.isOwner) disableButtons("You are not the lobby owner", this.inviteButton, this.kickButton)

            disableButtons("There are no players in the lobby", this.kickButton)
        }

        bottomFooterButtons.addChild(
            Button.builder(CommonComponents.GUI_BACK, { `_` -> this.onClose() }).width(74).build()
        )
        this.layout.visitWidgets(this::addRenderableWidget)
        this.repositionElements()
    }

    fun disableButtons(message: String, vararg buttons: Button?) {
        for (button in buttons) {
            button?.setTooltip(Tooltip.create(Component.literal(message)))
            button?.active = false
        }
    }

    fun enableButtons(vararg buttons: Button?) {
        for (button in buttons) {
            button?.setTooltip(null)
            button?.active = true
        }
    }

    var ticks = 0

    override fun tick() {
        super.tick()

        ticks++
        if (ticks < 100) return
        ticks = 0

        if (!Steamed.lobbyId.isSome()) return

        lobbyMembers.clear()
        LOGGER.info("asking steam nicely for the number of lobby members >_<")

        val n = Steamed.matchmaking?.getNumLobbyMembers(Steamed.lobbyId.unwrap())!!
        for (i in 1..n) {
            LOGGER.info("on $i of $n")

            val id = Steamed.matchmaking?.getLobbyMemberByIndex(Steamed.lobbyId.unwrap(), i - 1)
            val name = Steamed.friends?.getFriendPersonaName(id)

            LOGGER.info("$id (known as $name)")

            lobbyMembers.add(name ?: "unknown")
        }

    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, a)
        if (Steamed.lobbyId.isSome()) {
            var i = 0
            this.lobbyMembers.forEach {
                i++
                graphics.centeredText(
                    this.font,
                    Component.literal(
                        it
                    ),
                    this.width / 2,
                    50 + 20 * i,
                    -1
                )
            }


        }
    }

    override fun repositionElements() {
        this.layout.arrangeElements()
    }

    override fun onClose() {
        this.minecraft.gui.setScreen(this.lastScreen)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == KEY_F5) {
            this.screenExecutor.execute({ this.minecraft.setScreenAndShow(SteamLobbyScreen(this.lastScreen)) })
        }

        return super.keyPressed(event)
    }

    override fun canInterruptWithAnotherScreen(): Boolean {
        return false
    }

    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
    }
}
