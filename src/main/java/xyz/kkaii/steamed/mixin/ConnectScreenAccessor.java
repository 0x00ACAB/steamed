package xyz.kkaii.steamed.mixin;

import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ConnectScreen.class)
public interface ConnectScreenAccessor {
	@Invoker("updateStatus")
	void steamed$updateStatus(Component status);
}
