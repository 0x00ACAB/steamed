package xyz.kkaii.steamed.mixin;

import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import xyz.kkaii.steamed.SteamPlayers;

@Mixin(ChatListener.class)
public class ChatListenerMixin {
	@ModifyVariable(method = "handleSystemMessage", at = @At("HEAD"), argsOnly = true)
	private Component steamed$markSteamJoinLeave(Component message) {
		return SteamPlayers.steamJoinLeave(message);
	}
}
