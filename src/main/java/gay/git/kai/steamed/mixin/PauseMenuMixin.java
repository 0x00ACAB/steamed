package gay.git.kai.steamed.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import gay.git.kai.steamed.screen.SteamLobbyScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public class PauseMenuMixin extends Screen {
	protected PauseMenuMixin(Component title) {
		super(title);
	}

	@Inject(
			method = "createPauseMenu",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/GridLayout;arrangeElements()V", shift = At.Shift.BEFORE)
	)
	private void createPauseMenu(CallbackInfo ci, @Local(name = "helper") GridLayout.RowHelper helper) {
		Button lobbyButton = Button.builder(Component.literal("Steam Lobby"), _ ->
				this.minecraft.setScreen(new SteamLobbyScreen(this.minecraft.screen))
		).width(204).build();
		helper.addChild(lobbyButton, 2);
	}
}
