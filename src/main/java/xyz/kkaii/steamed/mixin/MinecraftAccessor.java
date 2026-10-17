//? if <26.1 {
/*package xyz.kkaii.steamed.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	@Invoker("createTitle")
	String steamed$createTitle();
}
*///?}
