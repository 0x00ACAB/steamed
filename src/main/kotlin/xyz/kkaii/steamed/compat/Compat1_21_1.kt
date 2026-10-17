//? if >=1.21 <26.1 {
/*@file:JvmName("Compat")

package xyz.kkaii.steamed.compat

import com.mojang.authlib.GameProfile
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.ImageButton
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.gui.components.WidgetSprites
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.layouts.LayoutElement
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.GenericMessageScreen
import net.minecraft.client.gui.screens.ProgressScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.client.resources.PlayerSkin
import net.minecraft.client.server.IntegratedServer
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.util.FastColor
import net.minecraft.world.level.block.entity.SkullBlockEntity
import xyz.kkaii.steamed.mixin.MinecraftAccessor

typealias Graphics = GuiGraphics
typealias ToastId = SystemToast.SystemToastId
fun toastId(displayMs: Long): ToastId = SystemToast.SystemToastId(displayMs)

fun resourceId(namespace: String, path: String): Identifier = Identifier.fromNamespaceAndPath(namespace, path)

fun lanServerData(name: String, ip: String): ServerData = ServerData(name, ip, ServerData.Type.LAN)
fun connectTo(parent: Screen, mc: Minecraft, address: ServerAddress, data: ServerData) =
    ConnectScreen.startConnecting(parent, mc, address, data, false, null)

fun spriteButton(x: Int, y: Int, sprite: String, onPress: Button.OnPress, label: Component): Button =
    ImageButton(x, y, 20, 20, WidgetSprites(resourceId("steamed", sprite), resourceId("steamed", "${sprite}_highlighted")), onPress, label)

typealias FlowLayout = LinearLayout
fun flowRow(spacing: Int): FlowLayout = LinearLayout.horizontal().spacing(spacing)
fun flowColumn(spacing: Int): FlowLayout =
    LinearLayout.vertical().spacing(spacing).also { it.defaultCellSetting().alignHorizontallyCenter() }

fun HeaderAndFooterLayout.titleHeader(title: Component, font: Font) {
    addTitleHeader(title, font)
}

fun Minecraft.currentScreen(): Screen? = screen
fun Minecraft.show(screen: Screen) = setScreen(screen)
fun Minecraft.closeTo(screen: Screen?) = setScreen(screen)
fun Minecraft.systemMessage(message: Component) = gui.chat.addMessage(message)
fun Minecraft.toast(id: ToastId, title: Component, message: Component?) =
    SystemToast.addOrUpdate(toasts, id, title, message)
fun Minecraft.disconnectTo(screen: Screen) = disconnect(screen, false)

// What 26.x's disconnectFromWorld does; 1.21.1's ClientLevel.disconnect takes no reason.
fun Minecraft.leaveWorld(@Suppress("UNUSED_PARAMETER") reason: Component) {
    val localServer = isLocalServer
    level?.disconnect()
    disconnectTo(if (localServer) GenericMessageScreen(Component.translatable("menu.savingLevel")) else ProgressScreen(true))
    setScreen(if (localServer) TitleScreen() else JoinMultiplayerScreen(TitleScreen()))
}

fun Minecraft.appendTitle(suffix: String) = window.setTitle((this as MinecraftAccessor).`steamed$createTitle`() + suffix)

fun Screen.pressKey(key: Int): Boolean = keyPressed(key, 0, 0)
fun AbstractWidget.draw(graphics: Graphics, mouseX: Int, mouseY: Int, a: Float) = render(graphics, mouseX, mouseY, a)

fun GuiGraphics.text(font: Font, text: Component, x: Int, y: Int, color: Int) {
    drawString(font, text, x, y, color)
}
fun GuiGraphics.text(font: Font, text: String, x: Int, y: Int, color: Int) {
    drawString(font, text, x, y, color)
}
fun GuiGraphics.centeredText(font: Font, text: Component, x: Int, y: Int, color: Int) =
    drawCenteredString(font, text, x, y, color)

// Fetched once per UUID; the default skin stands in until (or unless) the real one loads.
private val skins = ConcurrentHashMap<UUID, PlayerSkin>()
private val requested: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

private fun skin(uuid: UUID): PlayerSkin {
    skins[uuid]?.let { return it }
    if (requested.add(uuid)) {
        val skinManager = Minecraft.getInstance().skinManager
        SkullBlockEntity.fetchGameProfile(uuid)
            .thenCompose { profile: Optional<GameProfile> ->
                if (profile.isPresent) skinManager.getOrLoad(profile.get())
                else CompletableFuture.completedFuture<PlayerSkin?>(null)
            }
            .thenAccept { skin -> if (skin != null) skins[uuid] = skin }
    }
    return DefaultPlayerSkin.get(uuid)
}

fun Graphics.face(uuid: UUID, x: Int, y: Int, size: Int) = PlayerFaceRenderer.draw(this, skin(uuid), x, y, size)

fun minecraftVersionName(): String = SharedConstants.getCurrentVersion().name
fun argb(a: Int, r: Int, g: Int, b: Int): Int = FastColor.ARGB32.color(a, r, g, b)
fun fontStyle(font: Identifier): Style = Style.EMPTY.withFont(font)
fun Style.opensUrl(url: String, hover: Component): Style =
    withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, url)).withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))

fun publishForSteam(server: IntegratedServer, port: Int): Boolean =
    server.publishServer(server.defaultGameType, true, port)

fun <T : Any> cycleButton(name: (T) -> Component, initial: T): CycleButton.Builder<T> =
    CycleButton.builder(name).withInitialValue(initial)

abstract class SteamedScreen(title: Component) : Screen(title) {
    protected val mc: Minecraft get() = minecraft!!

    // 26.x only: whether a screen Minecraft wants to open may replace this one.
    open fun canInterruptWithAnotherScreen(): Boolean = true

    protected open fun draw(graphics: Graphics, mouseX: Int, mouseY: Int, a: Float) {}
    protected open fun onKey(key: Int) {}

    protected fun <T> addList(layout: HeaderAndFooterLayout, list: T): T where T : LayoutElement, T : ListInLayout =
        layout.addToContents(list)
    protected fun fitList(list: ListInLayout?, layout: HeaderAndFooterLayout) = list?.fit(this, layout)

    override fun render(graphics: Graphics, mouseX: Int, mouseY: Int, a: Float) {
        super.render(graphics, mouseX, mouseY, a)
        draw(graphics, mouseX, mouseY, a)
    }

    override fun keyPressed(key: Int, scanCode: Int, modifiers: Int): Boolean {
        onKey(key)
        return super.keyPressed(key, scanCode, modifiers)
    }
}

// The selection lists the screens lay out between a HeaderAndFooterLayout's header and footer.
interface ListInLayout {
    fun fit(screen: Screen, layout: HeaderAndFooterLayout)
}

abstract class ContainerList<E : ContainerObjectSelectionList.Entry<E>>(mc: Minecraft, width: Int, height: Int, y: Int, itemHeight: Int) :
    ContainerObjectSelectionList<E>(mc, width, height, y, itemHeight), ListInLayout {
    override fun fit(screen: Screen, layout: HeaderAndFooterLayout) = updateSize(screen.width, layout)
}

abstract class SelectionList<E : ObjectSelectionList.Entry<E>>(mc: Minecraft, width: Int, height: Int, y: Int, itemHeight: Int) :
    ObjectSelectionList<E>(mc, width, height, y, itemHeight), ListInLayout {
    override fun fit(screen: Screen, layout: HeaderAndFooterLayout) = updateSize(screen.width, layout)
}

abstract class ContainerRow<E : ContainerRow<E>> : ContainerObjectSelectionList.Entry<E>() {
    abstract fun draw(graphics: Graphics, x: Int, y: Int, width: Int, height: Int, mouseX: Int, mouseY: Int, a: Float)

    override fun render(
        graphics: Graphics, index: Int, top: Int, left: Int, width: Int, height: Int,
        mouseX: Int, mouseY: Int, hovered: Boolean, a: Float,
    ) = draw(graphics, left, top, width, height, mouseX, mouseY, a)
}

abstract class SelectionRow<E : SelectionRow<E>> : ObjectSelectionList.Entry<E>() {
    abstract fun draw(graphics: Graphics, x: Int, y: Int, width: Int, height: Int, mouseX: Int, mouseY: Int, a: Float)
    protected open fun onClick() {}

    override fun render(
        graphics: Graphics, index: Int, top: Int, left: Int, width: Int, height: Int,
        mouseX: Int, mouseY: Int, hovered: Boolean, a: Float,
    ) = draw(graphics, left, top, width, height, mouseX, mouseY, a)

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        onClick()
        return super.mouseClicked(mouseX, mouseY, button)
    }
}
*///?}
