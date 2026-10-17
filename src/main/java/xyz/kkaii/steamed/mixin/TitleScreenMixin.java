package xyz.kkaii.steamed.mixin;

import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.kkaii.steamed.SteamPlayers;
import xyz.kkaii.steamed.Steamed;
import xyz.kkaii.steamed.compat.Compat;
import xyz.kkaii.steamed.screen.SteamLobbyScreen;

@Mixin(TitleScreen.class)
public class TitleScreenMixin extends Screen {
	protected TitleScreenMixin(Component title) {
		super(title);
	}

	@Inject(at = @At("RETURN"), method = "init")
	public void init(CallbackInfo info) {
		// Leaves the lobby only if its world was running, so accepting an invite from here doesn't undo itself.
		Steamed.INSTANCE.onTitleScreen();
		SteamPlayers.INSTANCE.publishPresence();

		Component message = Steamed.friends == null ? Component.translatable("steamed.title.not_logged_in") : Component.translatable("steamed.title.logged_in", Steamed.friends.getPersonaName());
		TitleScreen thisObject = (TitleScreen) (Object) this;
		int width = this.font.width(message);
		int x = this.width - width - 2;
		// One line above vanilla's copyright, which sits at height - 10.
		this.addRenderableWidget(new PlainTextButton(x, thisObject.height - 20, width, 10, message, pressed -> Compat.show(this.minecraft, new SteamLobbyScreen(Compat.currentScreen(this.minecraft))), this.font));

		steamed$addLobbyButton();
	}

	// The mug icon, to the right of Multiplayer and the same height as the menu buttons.
	@Unique
	private void steamed$addLobbyButton() {
		for (GuiEventListener child : this.children()) {
			if (child instanceof AbstractWidget widget
					&& widget.getMessage().getContents() instanceof TranslatableContents contents
					&& contents.getKey().equals("menu.multiplayer")) {
				Component label = Component.translatable("steamed.lobby.button");
				Button button = Compat.spriteButton(
						widget.getX() + widget.getWidth() + 4, widget.getY(), "menu/lobby",
						pressed -> Compat.show(this.minecraft, new SteamLobbyScreen(this)), label
				);
				button.setTooltip(Tooltip.create(label));
				this.addRenderableWidget(button);
				return;
			}
		}
	}
}
