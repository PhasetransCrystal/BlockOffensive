package net.ptcrys.blockoffensive.mixin.tacz;

import net.ptcrys.blockoffensive.intro.client.IntroClientController;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.compat.playeranimator.animation.AnimationManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AnimationManager.class, remap = false)
public abstract class IntroTaczAnimationManagerMixin {

    @Inject(method = "hasPlayerAnimator3rd", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mcs2intro$forceVanillaThirdPersonGunPoseDuringIntro(GunDisplayInstance display, CallbackInfoReturnable<Boolean> cir) {
        if (IntroClientController.isCinematicActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "playLowerAnimation", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mcs2intro$blockLowerDuringIntro(AbstractClientPlayer player, GunDisplayInstance display, float limbSwingAmount, CallbackInfo ci) {
        mcs2intro$cancelDuringIntro(ci);
    }

    @Inject(method = "playLoopUpperAnimation", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mcs2intro$blockLoopUpperDuringIntro(AbstractClientPlayer player, GunDisplayInstance display, float limbSwingAmount, CallbackInfo ci) {
        mcs2intro$cancelDuringIntro(ci);
    }

    @Inject(method = "playRotationAnimation", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mcs2intro$blockRotationDuringIntro(AbstractClientPlayer player, GunDisplayInstance display, CallbackInfo ci) {
        mcs2intro$cancelDuringIntro(ci);
    }

    @Inject(method = "playLoopAnimation", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mcs2intro$blockLoopDuringIntro(AbstractClientPlayer player, GunDisplayInstance display, ResourceLocation dataId, String animationName, CallbackInfo ci) {
        mcs2intro$cancelDuringIntro(ci);
    }

    @Inject(method = "playOnceAnimation", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mcs2intro$blockOnceDuringIntro(AbstractClientPlayer player, GunDisplayInstance display, ResourceLocation dataId, String animationName, CallbackInfo ci) {
        mcs2intro$cancelDuringIntro(ci);
    }

    private static void mcs2intro$cancelDuringIntro(CallbackInfo ci) {
        if (IntroClientController.isCinematicActive()) {
            IntroClientController.recordExternalAnimationBlocked("taczPlayerAnimator");
            ci.cancel();
        }
    }
}
