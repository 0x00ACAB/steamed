package xyz.kkaii.steamed.screen

import com.codedisaster.steamworks.SteamID
import com.codedisaster.steamworks.SteamMatchmaking
import com.mojang.blaze3d.platform.InputConstants.KEY_F5
import com.mojang.logging.LogUtils
import net.minecraft.ChatFormatting
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.*
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.resources.language.I18n
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.minecraft.world.item.component.ResolvableProfile
import org.slf4j.Logger
import xyz.kkaii.steamed.FriendLobbies
import xyz.kkaii.steamed.SteamInit
import xyz.kkaii.steamed.SteamPlayers
import xyz.kkaii.steamed.Steamed
import xyz.kkaii.steamed.platform.Services
import java.util.*

class SteamLobbyScreen(val lastScreen: Screen?) : Screen(Component.translatable("steamed.lobby.title")) {
    private val layout: HeaderAndFooterLayout = HeaderAndFooterLayout(this, 33, 60)
    private var inviteButton: Button? = null
    private var createLobby: Button? = null
    private var joinButton: Button? = null
    private var memberList: MemberList? = null
    private var hostList: HostList? = null

    private var isInsideLobby = false
    private var isOwner = false

    // Opening the screen retries Steam, so starting it after Minecraft just works.
    private val steamAvailable = SteamInit.connect()

    init {
        this.isInsideLobby = Steamed.lobbyId.isSome()
        if (this.isInsideLobby) this.isOwner =
            Steamed.matchmaking?.getLobbyOwner(Steamed.lobbyId.unwrap()) == Steamed.user?.steamID
    }

    override fun init() {
        this.layout.addTitleHeader(this.title, this.font)
        if (!steamAvailable) {
            initWithoutSteam()
            return
        }

        if (this.isInsideLobby) {
            this.memberList = this.layout.addToContents(MemberList(this.minecraft))
        } else {
            this.hostList = this.layout.addToContents(HostList(this.minecraft))
        }
        val footer: LinearLayout = this.layout.addToFooter(LinearLayout.vertical().spacing(4))
        footer.defaultCellSetting().alignHorizontallyCenter()
        val topFooterButtons: LinearLayout = footer.addChild(LinearLayout.horizontal().spacing(4))
        val bottomFooterButtons: LinearLayout = footer.addChild(LinearLayout.horizontal().spacing(4))

        if (!this.isInsideLobby) {
            this.createLobby = topFooterButtons.addChild(Button.builder(Component.translatable("steamed.lobby.create"), { self ->
                Steamed.creatingLobbyType = lobbyType
                Steamed.matchmaking?.createLobby(lobbyType, maxPlayers)?.isValid?.let {
                    if (!it) {
                        LOGGER.error("Failed to create lobby")
                        return@builder
                    }
                }
                self.active = false
            }).width(150).build())

            topFooterButtons.addChild(
                CycleButton.builder<SteamMatchmaking.LobbyType>({ lobbyTypeName(it) }, lobbyType)
                    .withValues(SteamMatchmaking.LobbyType.FriendsOnly, SteamMatchmaking.LobbyType.Private)
                    .displayOnlyValue()
                    .withTooltip { Tooltip.create(lobbyTypeTooltip(it)) }
                    .create(0, 0, 76, 20, Component.translatable("steamed.lobby.visibility")) { _, value -> lobbyType = value }
            )

            bottomFooterButtons.addChild(
                CycleButton.builder<Int>({ Component.literal(it.toString()) }, maxPlayers)
                    .withValues(MAX_PLAYER_CHOICES)
                    .withTooltip { Tooltip.create(Component.translatable("steamed.lobby.max_players.tooltip")) }
                    .create(0, 0, 152, 20, Component.translatable("steamed.lobby.max_players")) { _, value ->
                        maxPlayers = value
                    }
            )

            if (!this.minecraft.hasSingleplayerServer()) {
                disableButtons(Component.translatable("steamed.lobby.need_world"), this.createLobby)
            }
        } else {
            if (!this.isOwner) {
                this.joinButton = topFooterButtons.addChild(Button.builder(Component.translatable("steamed.lobby.join_host"), { `_` ->
                    Steamed.joinHostedLobby()
                }).width(113).build())
            }
            topFooterButtons.addChild(
                Button.builder(Component.translatable("steamed.lobby.leave"), { `_` -> leaveLobby() })
                    .width(if (this.isOwner) 230 else 113).build()
            )

            this.inviteButton = bottomFooterButtons.addChild(Button.builder(Component.translatable("steamed.lobby.invite"), { `_` ->
                this.minecraft.setScreenAndShow(SteamFriendListScreen(this))
            }).width(113).build())
            if (!this.isOwner) disableButtons(Component.translatable("steamed.lobby.not_owner"), this.inviteButton)
        }

        bottomFooterButtons.addChild(
            Button.builder(CommonComponents.GUI_BACK, { `_` -> this.onClose() })
                .width(if (this.isInsideLobby) 113 else 74).build()
        )
        this.layout.visitWidgets(this::addRenderableWidget)
        this.repositionElements()
    }

    private fun initWithoutSteam() {
        this.layout.addToContents(
            MultiLineTextWidget(Component.translatable("steamed.steam.unavailable"), this.font)
                .setMaxWidth(260)
                .setCentered(true)
        )
        val footer: LinearLayout = this.layout.addToFooter(LinearLayout.horizontal().spacing(4))
        val retryLabel = Component.translatable("steamed.steam.retry")
        footer.addChild(
            ImageButton(0, 0, 20, 20, RETRY_SPRITES, { _ -> retrySteam() }, retryLabel)
                .also { it.setTooltip(Tooltip.create(retryLabel)) }
        )
        footer.addChild(Button.builder(CommonComponents.GUI_BACK, { _ -> this.onClose() }).width(150).build())
        this.layout.visitWidgets(this::addRenderableWidget)
        this.repositionElements()
    }

    private fun retrySteam() {
        val connected = SteamInit.connect()
        val friends = Steamed.friends
        SystemToast.addOrUpdate(
            this.minecraft.gui.toastManager(), RETRY_TOAST,
            Component.translatable(if (connected) "steamed.steam.retry.connected" else "steamed.steam.retry.failed"),
            if (connected && friends != null) Component.translatable("steamed.title.logged_in", friends.personaName)
            else Component.translatable("steamed.steam.retry.failed.hint")
        )
        if (connected) this.minecraft.setScreenAndShow(SteamLobbyScreen(this.lastScreen))
    }

    private fun leaveLobby() {
        val connectedToHost = Steamed.isConnectedToLobbyHost()
        Steamed.leaveLobby()
        if (connectedToHost) {
            this.minecraft.disconnectFromWorld(Component.translatable("steamed.lobby.left"))
        } else {
            this.minecraft.setScreenAndShow(SteamLobbyScreen(this.lastScreen))
        }
    }

    fun disableButtons(message: Component, vararg buttons: Button?) {
        for (button in buttons) {
            button?.setTooltip(Tooltip.create(message))
            button?.active = false
        }
    }

    private var ticks = 0

    override fun tick() {
        super.tick()
        if (++ticks % 20 == 0) {
            this.memberList?.refresh()
            this.hostList?.refresh()
        }
    }

    override fun repositionElements() {
        this.layout.arrangeElements()
        this.memberList?.updateSize(this.width, this.layout)
        this.hostList?.updateSize(this.width, this.layout)
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

    private inner class MemberList(minecraft: Minecraft) : ContainerObjectSelectionList<MemberList.Entry>(
        minecraft, this@SteamLobbyScreen.width, this@SteamLobbyScreen.height - 33 - 60, 33, 36
    ) {
        private var members: List<SteamID> = emptyList()

        init {
            refresh()
        }

        fun refresh() {
            val matchmaking = Steamed.matchmaking ?: return
            if (!Steamed.lobbyId.isSome()) return
            val lobby = Steamed.lobbyId.unwrap()!!
            val current =
                (0..<matchmaking.getNumLobbyMembers(lobby)).map { matchmaking.getLobbyMemberByIndex(lobby, it) }
            if (current != members) {
                members = current
                val owner = matchmaking.getLobbyOwner(lobby)
                // Host first, then everyone else in Steam's order.
                this.replaceEntries(current.sortedBy { it != owner }.map { Entry(lobby, it, it == owner) })
            } else {
                this.children().forEach { it.resolveHead() }
            }
        }

        override fun getRowWidth(): Int = 260

        inner class Entry(private val lobby: SteamID, val steamId: SteamID, private val isHost: Boolean) :
            ContainerObjectSelectionList.Entry<Entry>() {
            private val name: String = Steamed.friends?.getFriendPersonaName(steamId) ?: I18n.get("steamed.lobby.unknown_member")
            private val isSelf = steamId == Steamed.user?.steamID
            private var head: ResolvableProfile? = null
            private var kicked = Steamed.kickedPeers.contains(steamId)
            private val kickButton: Button? =
                if (this@SteamLobbyScreen.isOwner && !isSelf) {
                    rowButton(Component.translatable("steamed.lobby.kick"), 40) { button ->
                        Steamed.kick(steamId)
                        kicked = true
                        button.active = false
                        LOGGER.info("Kicked {} from the lobby", name)
                    }.also { it.active = !kicked }
                } else null
            private val widgets: List<AbstractWidget> = listOfNotNull(kickButton)

            init {
                resolveHead()
            }

            fun resolveHead() {
                if (head == null) head =
                    SteamPlayers.memberUuid(lobby, steamId)?.let { ResolvableProfile.createUnresolved(it) }
            }

            private fun status(): Component? = when {
                kicked -> Component.translatable("steamed.lobby.status.kicked").withStyle(ChatFormatting.RED)
                isHost && isSelf -> Component.translatable("steamed.lobby.status.host_you")
                isHost -> Component.translatable("steamed.lobby.status.host")
                isSelf -> Component.translatable("steamed.lobby.status.you")
                else -> null
            }

            override fun extractContent(
                graphics: GuiGraphicsExtractor,
                mouseX: Int,
                mouseY: Int,
                hovered: Boolean,
                a: Float
            ) {
                graphics.fill(this.contentX, this.contentY, this.contentRight, this.contentBottom, BG_FILL)
                val faceX = this.contentX + 4
                val faceY = this.contentY + (this.contentHeight - 24) / 2
                head?.let { PlayerFaceExtractor.extractRenderState(graphics, it, faceX, faceY, 24) }

                val textX = faceX + 24 + 4
                val status = status()
                val font = this@SteamLobbyScreen.font
                if (status == null) {
                    graphics.text(font, name, textX, this.contentY + (this.contentHeight - 9) / 2, -1)
                } else {
                    val textY = this.contentY + (this.contentHeight - 18) / 2
                    graphics.text(font, name, textX, textY, -1)
                    graphics.text(font, status, textX, textY + 12, STATUS_COLOR)
                }

                kickButton?.let {
                    it.setX(this.contentRight - it.width - 4)
                    it.setY(this.contentY + (this.contentHeight - it.height) / 2)
                    it.extractRenderState(graphics, mouseX, mouseY, a)
                }
            }

            override fun children(): List<GuiEventListener> = widgets

            override fun narratables(): List<NarratableEntry> = widgets
        }
    }

    // Outside a lobby: a pending invite, then the Steamed lobbies friends are in.
    private inner class HostList(minecraft: Minecraft) : ContainerObjectSelectionList<HostList.Row>(
        minecraft, this@SteamLobbyScreen.width, this@SteamLobbyScreen.height - 33 - 60, 33, 36
    ) {
        private var shown: List<Any?> = listOf(Unit)

        init {
            refresh()
        }

        fun refresh() {
            val invite = Steamed.pendingInvite
            val hosted = FriendLobbies.scan()
            val key = listOf<Any?>(invite) + hosted
            if (key == shown) return
            shown = key
            val rows = mutableListOf<Row>()
            invite?.let { rows += InviteRow(it) }
            hosted.forEach { rows += HostRow(it) }
            if (rows.isEmpty()) rows += EmptyRow()
            this.replaceEntries(rows)
        }

        override fun getRowWidth(): Int = 300

        abstract inner class Row : ContainerObjectSelectionList.Entry<Row>() {
            protected abstract val buttons: List<AbstractWidget>
            protected val font get() = this@SteamLobbyScreen.font

            protected fun drawRow(graphics: GuiGraphicsExtractor, face: UUID?, title: Component, subtitle: Component?) {
                graphics.fill(this.contentX, this.contentY, this.contentRight, this.contentBottom, BG_FILL)
                val faceX = this.contentX + 4
                face?.let {
                    PlayerFaceExtractor.extractRenderState(
                        graphics,
                        ResolvableProfile.createUnresolved(it),
                        faceX,
                        this.contentY + (this.contentHeight - 24) / 2,
                        24
                    )
                }
                val textX = faceX + 24 + 4
                if (subtitle == null) {
                    graphics.text(font, title, textX, this.contentY + (this.contentHeight - 9) / 2, -1)
                } else {
                    val textY = this.contentY + (this.contentHeight - 18) / 2
                    graphics.text(font, title, textX, textY, -1)
                    graphics.text(font, subtitle, textX, textY + 12, STATUS_COLOR)
                }
            }

            protected fun placeButtons(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
                var x = this.contentRight - 4
                for (button in buttons.reversed()) {
                    x -= button.width
                    button.setX(x)
                    button.setY(this.contentY + (this.contentHeight - button.height) / 2)
                    button.extractRenderState(graphics, mouseX, mouseY, a)
                    x -= 4
                }
            }

            override fun children(): List<GuiEventListener> = buttons

            override fun narratables(): List<NarratableEntry> = buttons
        }

        inner class InviteRow(private val invite: Steamed.Invite) : Row() {
            override val buttons: List<AbstractWidget> = listOf(
                rowButton(Component.translatable("steamed.lobby.accept"), 50) { _ ->
                    Steamed.acceptInvite()
                    this@SteamLobbyScreen.onClose()
                },
                rowButton(Component.translatable("steamed.lobby.decline"), 50) { _ ->
                    Steamed.declineInvite()
                    refresh()
                },
            )

            override fun extractContent(
                graphics: GuiGraphicsExtractor,
                mouseX: Int,
                mouseY: Int,
                hovered: Boolean,
                a: Float
            ) {
                drawRow(
                    graphics,
                    SteamPlayers.friendUuid(invite.inviter),
                    Component.literal(invite.inviterName),
                    Component.translatable("steamed.lobby.invited_you")
                )
                placeButtons(graphics, mouseX, mouseY, a)
            }
        }

        inner class HostRow(private val hosted: FriendLobbies.Hosted) : Row() {
            override val buttons: List<AbstractWidget> = listOf(
                rowButton(Component.translatable("steamed.lobby.join"), 40) { _ ->
                    Steamed.joinLobby(hosted.lobby)
                    this@SteamLobbyScreen.onClose()
                }.also {
                    if (!hosted.compatible) {
                        it.active = false
                        it.setTooltip(Tooltip.create(
                            if (!hosted.sameMinecraft) Component.translatable(
                                "steamed.lobby.minecraft_mismatch", hosted.mcVersion, SharedConstants.getCurrentVersion().name()
                            )
                            else Component.translatable("steamed.lobby.version_mismatch", hosted.version, Services.PLATFORM.modVersion())
                        ))
                    }
                },
            )

            override fun extractContent(
                graphics: GuiGraphicsExtractor,
                mouseX: Int,
                mouseY: Int,
                hovered: Boolean,
                a: Float
            ) {
                val names = hosted.friends.joinToString(", ") { it.second }
                val world = hosted.world.ifEmpty { I18n.get("steamed.lobby.a_world") }
                val subtitle = if (hosted.compatible) Component.translatable("steamed.lobby.playing", world)
                else if (!hosted.sameMinecraft) Component.translatable("steamed.lobby.minecraft", hosted.mcVersion).withStyle(ChatFormatting.RED)
                else Component.translatable("steamed.lobby.version", hosted.version).withStyle(ChatFormatting.RED)
                drawRow(
                    graphics,
                    SteamPlayers.friendUuid(hosted.friends.first().first),
                    Component.literal(names),
                    subtitle
                )
                placeButtons(graphics, mouseX, mouseY, a)
            }
        }

        inner class EmptyRow : Row() {
            override val buttons: List<AbstractWidget> = emptyList()

            override fun extractContent(
                graphics: GuiGraphicsExtractor,
                mouseX: Int,
                mouseY: Int,
                hovered: Boolean,
                a: Float
            ) {
                graphics.centeredText(
                    font,
                    Component.translatable("steamed.lobby.no_friends_hosting"),
                    this.contentX + this.contentWidth / 2,
                    this.contentY + (this.contentHeight - 9) / 2,
                    STATUS_COLOR
                )
            }
        }
    }

    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
        private val BG_FILL = ARGB.color(255, 74, 74, 74)
        private val RETRY_TOAST = SystemToast.SystemToastId(4000L)
        private val RETRY_SPRITES = WidgetSprites(
            Identifier.fromNamespaceAndPath("steamed", "menu/retry"),
            Identifier.fromNamespaceAndPath("steamed", "menu/retry_highlighted")
        )
        private val STATUS_COLOR = ARGB.color(140, 255, 255, 255)

        private var lobbyType = SteamMatchmaking.LobbyType.FriendsOnly

        // Steam's own limit is 250. The host counts as one.
        private val MAX_PLAYER_CHOICES = listOf(2, 4, 8, 16, 250)
        private var maxPlayers = 8

        // Widens past the default for translations with longer labels.
        private fun rowButton(message: Component, minWidth: Int, onPress: Button.OnPress): Button =
            Button.builder(message, onPress)
                .width(maxOf(minWidth, Minecraft.getInstance().font.width(message) + 10))
                .build()

        private fun lobbyTypeName(type: SteamMatchmaking.LobbyType): Component = when (type) {
            SteamMatchmaking.LobbyType.Private -> Component.translatable("steamed.visibility.private")
            else -> Component.translatable("steamed.visibility.friends")
        }

        private fun lobbyTypeTooltip(type: SteamMatchmaking.LobbyType): Component = when (type) {
            SteamMatchmaking.LobbyType.Private -> Component.translatable("steamed.visibility.private.tooltip")
            else -> Component.translatable("steamed.visibility.friends.tooltip")
        }
    }
}
