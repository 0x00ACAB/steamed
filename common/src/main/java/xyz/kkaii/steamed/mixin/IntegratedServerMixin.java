package xyz.kkaii.steamed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import xyz.kkaii.steamed.Steamed;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.server.LanServerPinger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.net.InetAddress;

@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {
	// A world hosted through Steam is only meant to be reached through the P2P relay, so keep it off the LAN.
	@ModifyArg(
			method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;I)Z",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerConnectionListener;startTcpServerListener(Ljava/net/InetAddress;I)V"),
			index = 0
	)
	private InetAddress steamed$bindLoopback(InetAddress address) {
		return Steamed.publishingForSteam ? InetAddress.getLoopbackAddress() : address;
	}

	@WrapOperation(
			method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;I)Z",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/LanServerPinger;start()V")
	)
	private void steamed$skipLanBroadcast(LanServerPinger pinger, Operation<Void> original) {
		if (!Steamed.publishingForSteam) original.call(pinger);
	}
}
