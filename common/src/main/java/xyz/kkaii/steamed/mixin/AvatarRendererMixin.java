package xyz.kkaii.steamed.mixin;

import xyz.kkaii.steamed.SteamPlayers;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
	@Inject(
			method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL")
	)
	private void markSteamPlayer(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		if (state.nameTag != null) state.nameTag = SteamPlayers.decorate(entity.getUUID(), state.nameTag);
	}
}
