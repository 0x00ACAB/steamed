package xyz.kkaii.steamed.mixin;

import xyz.kkaii.steamed.SteamPlayers;
import xyz.kkaii.steamed.Steamed;
import xyz.kkaii.steamed.screen.SteamLobbyScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin extends Screen {
	@Unique
	private static final WidgetSprites STEAMED_LOBBY_SPRITES = new WidgetSprites(
			Identifier.fromNamespaceAndPath("steamed", "menu/lobby"),
			Identifier.fromNamespaceAndPath("steamed", "menu/lobby_highlighted")
	);

	protected TitleScreenMixin(Component title) {
		super(title);
	}

	@Inject(at = @At("RETURN"), method = "init")
	public void init(CallbackInfo info) {
		// Leaves the lobby only if its world was running, so accepting an invite from here doesn't undo itself.
		Steamed.INSTANCE.onTitleScreen();
		SteamPlayers.INSTANCE.publishPresence();

		String message = Steamed.friends == null ? "Not logged into Steam" : String.format("Logged in as %s", Steamed.friends.getPersonaName());
		TitleScreen thisObject = (TitleScreen) (Object) this;
		int width = this.font.width(message);
		int x = this.width - width - 2;
		this.addRenderableWidget(new PlainTextButton(x, thisObject.height - 32, width, 15, Component.literal(message), _ -> this.minecraft.setScreenAndShow(new SteamLobbyScreen(this.minecraft.gui.screen())), this.font));

		steamed$addLobbyButton();
	}

	// The mug icon, to the right of Multiplayer and the same height as the menu buttons.
	@Unique
	private void steamed$addLobbyButton() {
		for (GuiEventListener child : this.children()) {
			if (child instanceof AbstractWidget widget
					&& widget.getMessage().getContents() instanceof TranslatableContents contents
					&& contents.getKey().equals("menu.multiplayer")) {
				Component label = Component.literal("Steam Lobby");
				ImageButton button = new ImageButton(
						widget.getX() + widget.getWidth() + 4, widget.getY(), 20, 20, STEAMED_LOBBY_SPRITES,
						_ -> this.minecraft.setScreenAndShow(new SteamLobbyScreen(this)), label
				);
				button.setTooltip(Tooltip.create(label));
				this.addRenderableWidget(button);
				return;
			}
		}
	}
}
