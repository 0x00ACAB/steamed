//? if <26.1 {
/*package xyz.kkaii.steamed.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.server.IntegratedPlayerList;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.kkaii.steamed.Steamed;

@Mixin(PlayerList.class)
public class PlayerListMixin {
	// Vanilla caps an integrated server at 8 players, below what the lobby may allow.
	@Inject(method = "getMaxPlayers", at = @At("RETURN"), cancellable = true)
	private void steamed$lobbyMaxPlayers(CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(steamed$cap(cir.getReturnValueI()));
	}

	@ModifyExpressionValue(
			method = "canPlayerLogin",
			at = @At(value = "FIELD", target = "Lnet/minecraft/server/players/PlayerList;maxPlayers:I")
	)
	private int steamed$lobbyLoginCap(int max) {
		return steamed$cap(max);
	}

	@Unique
	private int steamed$cap(int max) {
		return (Object) this instanceof IntegratedPlayerList && Steamed.hostedMaxPlayers > max ? Steamed.hostedMaxPlayers : max;
	}
}
*///?}
