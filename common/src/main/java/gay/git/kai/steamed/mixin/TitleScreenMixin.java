package gay.git.kai.steamed.mixin;

import gay.git.kai.steamed.Steamed;
import gay.git.kai.steamed.screen.SteamLobbyScreen;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin extends Screen {
	protected TitleScreenMixin(Component title) {
		super(title);
	}

	@Inject(at = @At("RETURN"), method = "init")
	public void init(CallbackInfo info) {
		String message = Steamed.friends == null ? "Not logged into Steam" : String.format("Logged in as %s", Steamed.friends.getPersonaName());
		TitleScreen thisObject = (TitleScreen) (Object) this;
		int width = this.font.width(message);
		int x = this.width - width - 2;
		this.addRenderableWidget(new PlainTextButton(x, thisObject.height - 32, width, 15, Component.literal(message), _ -> this.minecraft.setScreenAndShow(new SteamLobbyScreen(this.minecraft.gui.screen())), this.font));
	}
}