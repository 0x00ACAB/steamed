//? if <26.1 {
/*package xyz.kkaii.steamed.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import xyz.kkaii.steamed.SteamPlayers;

@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {
	@ModifyVariable(
			method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
			at = @At("HEAD"),
			argsOnly = true
	)
	private Component steamed$markSteamPlayer(Component name, @Local(argsOnly = true) AbstractClientPlayer player) {
		return SteamPlayers.decorate(player.getUUID(), name);
	}
}
*///?}
