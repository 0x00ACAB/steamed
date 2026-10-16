package xyz.kkaii.steamed.mixin;

import xyz.kkaii.steamed.Splashes;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.resources.SplashManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(SplashManager.class)
public class SplashManagerMixin {
	@Shadow
	private List<Component> splashes;

	@Inject(method = "apply(Ljava/util/List;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("TAIL"))
	private void steamed$addSplashes(List<Component> preparations, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
		List<Component> all = new ArrayList<>(this.splashes);
		all.addAll(Splashes.extraSplashes());
		this.splashes = List.copyOf(all);
	}

	@Inject(method = "getSplash", at = @At("RETURN"), cancellable = true)
	private void steamed$personalSplash(CallbackInfoReturnable<SplashRenderer> cir) {
		SplashRenderer vanilla = cir.getReturnValue();
		// Holiday splashes win; so does an empty pool (no splash at all).
		if (vanilla == null || vanilla == SplashRenderer.CHRISTMAS || vanilla == SplashRenderer.NEW_YEAR || vanilla == SplashRenderer.HALLOWEEN) return;
		Component personal = Splashes.personalSplash();
		if (personal != null) cir.setReturnValue(new SplashRenderer(personal));
	}
}
