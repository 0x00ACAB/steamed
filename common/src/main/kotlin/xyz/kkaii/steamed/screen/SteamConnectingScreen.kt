package xyz.kkaii.steamed.screen

import xyz.kkaii.steamed.Steamed
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.LoadingDotsText
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.util.Util

// Shown while the Steam auth handshake runs, before Minecraft's own ConnectScreen takes over.
class SteamConnectingScreen(val lastScreen: Screen?, private val hostName: String) :
    Screen(Component.literal("Joining $hostName")) {
    private var failure: Component? = null
    private var button: Button? = null

    override fun init() {
        this.button = this.addRenderableWidget(
            Button.builder(if (failure == null) CommonComponents.GUI_CANCEL else CommonComponents.GUI_BACK) { _ -> this.onClose() }
                .bounds(this.width / 2 - 100, this.height / 4 + 120 + 12, 200, 20)
                .build()
        )
    }

    fun fail(reason: String) {
        failure = Component.literal("Couldn't join $hostName: $reason")
        button?.message = CommonComponents.GUI_BACK
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, a)
        graphics.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 50, -1)
        val status = failure ?: Component.literal("Connecting through Steam")
        graphics.centeredText(this.font, status, this.width / 2, this.height / 2 - 30, -6250336)
        if (failure == null) {
            graphics.centeredText(this.font, Component.literal(LoadingDotsText.get(Util.getMillis())), this.width / 2, this.height / 2 - 16, -8355712)
        }
    }

    override fun onClose() {
        if (failure == null) Steamed.leaveLobby()
        // The lobby screen caches whether we're in a lobby, so rebuild it.
        val target = (lastScreen as? SteamLobbyScreen)?.let { SteamLobbyScreen(it.lastScreen) } ?: lastScreen
        this.minecraft.gui.setScreen(target)
    }

    override fun shouldCloseOnEsc(): Boolean = true
}
