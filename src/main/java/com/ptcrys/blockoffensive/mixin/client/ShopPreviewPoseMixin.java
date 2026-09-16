package com.ptcrys.blockoffensive.mixin.client;

import com.ptcrys.blockoffensive.client.shop.ShopPlayerPreview;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public class ShopPreviewPoseMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void blockoffensive$shopPose(LivingEntity entity, float swing, float amount,
                                        float age, float yaw, float pitch, CallbackInfo ci) {
        if (entity instanceof ShopPlayerPreview preview) {
            preview.applyPresentationPose((PlayerModel<?>) (Object) this);
        }
    }
}
