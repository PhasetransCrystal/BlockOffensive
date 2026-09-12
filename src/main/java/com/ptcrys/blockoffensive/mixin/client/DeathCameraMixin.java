package com.ptcrys.blockoffensive.mixin.client;

import com.ptcrys.blockoffensive.client.spec.KillCamManager;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Camera.class, priority = 900)
public class DeathCameraMixin {
    @Inject(method = "setup", at = @At("RETURN"))
    private void blockoffensive$deathCamera(BlockGetter level, Entity entity, boolean detached,
                                            boolean reverse, float partialTick, CallbackInfo ci) {
        KillCamManager.applyDeathCamera((Camera) (Object) this, partialTick);
    }
}
