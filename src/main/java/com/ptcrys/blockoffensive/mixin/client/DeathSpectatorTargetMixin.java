package com.ptcrys.blockoffensive.mixin.client;

import com.ptcrys.blockoffensive.client.spec.KillCamManager;
import com.ptcrys.fpsmatch.common.client.net.SpectatorTargetClientHandler;
import com.ptcrys.fpsmatch.common.packet.spec.SpectatorTargetS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SpectatorTargetClientHandler.class, remap = false)
public class DeathSpectatorTargetMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private static void blockoffensive$holdDeathCamera(SpectatorTargetS2CPacket packet, CallbackInfo ci) {
        if (KillCamManager.deferSpectatorTarget()) ci.cancel();
    }

    @Inject(method = "handle", at = @At("RETURN"))
    private static void blockoffensive$acknowledgeTarget(SpectatorTargetS2CPacket packet, CallbackInfo ci) {
        KillCamManager.spectatorTargetReceived();
    }
}
