package net.ptcrys.blockoffensive.mixin.client;

import net.ptcrys.blockoffensive.client.spec.DeathWorldMask;

import net.minecraft.client.renderer.GameRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class DeathWorldRenderMixin {

    @Inject(method = "render",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/renderer/PostChain;process(F)V"))
    private void blockoffensive$updateDeathEffect(float partialTick, long nanoTime,
                                                  boolean renderLevel, CallbackInfo ci) {
        DeathWorldMask.updateProgress(partialTick);
    }
}
