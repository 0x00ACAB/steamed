//? if >=26.1 {
@file:JvmName("Compat")

package xyz.kkaii.steamed.compat

import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.server.IntegratedServer
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.util.ARGB
import net.minecraft.world.item.component.ResolvableProfile
import org.lwjgl.glfw.GLFW
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

typealias Graphics = GuiGraphicsExtractor

fun Minecraft.currentScreen(): Screen? = gui.screen()
fun Minecraft.show(screen: Screen) = setScreenAndShow(screen)
fun Minecraft.closeTo(screen: Screen?) = gui.setScreen(screen)
fun Minecraft.systemMessage(message: Component) = gui.hud.chat.addClientSystemMessage(message)
fun Minecraft.toast(id: SystemToast.SystemToastId, title: Component, message: Component?) =
    SystemToast.addOrUpdate(gui.toastManager(), id, title, message)
fun Minecraft.disconnectTo(screen: Screen) = disconnect(screen, false)
fun Minecraft.leaveWorld(reason: Component) = disconnectFromWorld(reason)
fun Minecraft.appendTitle(suffix: String) = window.setTitle(GLFW.glfwGetWindowTitle(window.handle()) + suffix)

fun Screen.pressKey(key: Int): Boolean = keyPressed(KeyEvent(key, 0, 0))
fun AbstractWidget.draw(graphics: Graphics, mouseX: Int, mouseY: Int, a: Float) =
    extractRenderState(graphics, mouseX, mouseY, a)

private val profiles = ConcurrentHashMap<UUID, ResolvableProfile>()
fun Graphics.face(uuid: UUID, x: Int, y: Int, size: Int) =
    PlayerFaceExtractor.extractRenderState(this, profiles.computeIfAbsent(uuid, ResolvableProfile::createUnresolved), x, y, size)

fun minecraftVersionName(): String = SharedConstants.getCurrentVersion().name()
fun argb(a: Int, r: Int, g: Int, b: Int): Int = ARGB.color(a, r, g, b)
fun fontStyle(font: Identifier): Style = Style.EMPTY.withFont(FontDescription.Resource(font))
fun Style.opensUrl(url: String, hover: Component): Style =
    withClickEvent(ClickEvent.OpenUrl(URI(url))).withHoverEvent(HoverEvent.ShowText(hover))

fun publishForSteam(server: IntegratedServer, port: Int): Boolean =
    server.publishServer(MinecraftServer.MultiplayerScope.LAN, server.defaultGameType, true, port)

fun <T : Any> cycleButton(name: (T) -> Component, initial: T): CycleButton.Builder<T> = CycleButton.builder(name, initial)

abstract class SteamedScreen(title: Component) : Screen(title) {
    protected open fun draw(graphics: Graphics, mouseX: Int, mouseY: Int, a: Float) {}
    protected open fun onKey(key: Int) {}

    override fun extractRenderState(graphics: Graphics, mouseX: Int, mouseY: Int, a: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, a)
        draw(graphics, mouseX, mouseY, a)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        onKey(event.key())
        return super.keyPressed(event)
    }
}

abstract class ContainerRow<E : ContainerRow<E>> : ContainerObjectSelectionList.Entry<E>() {
    abstract fun draw(graphics: Graphics, x: Int, y: Int, width: Int, height: Int, mouseX: Int, mouseY: Int, a: Float)

    override fun extractContent(graphics: Graphics, mouseX: Int, mouseY: Int, hovered: Boolean, a: Float) =
        draw(graphics, contentX, contentY, contentWidth, contentHeight, mouseX, mouseY, a)
}

abstract class SelectionRow<E : SelectionRow<E>> : ObjectSelectionList.Entry<E>() {
    abstract fun draw(graphics: Graphics, x: Int, y: Int, width: Int, height: Int, mouseX: Int, mouseY: Int, a: Float)
    protected open fun onClick() {}

    override fun extractContent(graphics: Graphics, mouseX: Int, mouseY: Int, hovered: Boolean, a: Float) =
        draw(graphics, contentX, contentY, contentWidth, contentHeight, mouseX, mouseY, a)

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        onClick()
        return super.mouseClicked(event, doubleClick)
    }
}
//?}
