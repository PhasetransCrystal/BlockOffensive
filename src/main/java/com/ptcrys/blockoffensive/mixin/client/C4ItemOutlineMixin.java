package com.ptcrys.blockoffensive.mixin.client;

import com.ptcrys.blockoffensive.item.BOItemRegister;
import com.ptcrys.fpsmatch.common.client.FPSMClient;
import com.ptcrys.fpsmatch.common.client.data.FPSMClientGlobalData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(Entity.class)
public abstract class C4ItemOutlineMixin {

    private static final int T_C4_OUTLINE_COLOR = 0xEAC055;
    private static final double C4_OUTLINE_RENDER_DISTANCE = 128.0D;
    private static final double C4_OUTLINE_RENDER_DISTANCE_SQR = C4_OUTLINE_RENDER_DISTANCE * C4_OUTLINE_RENDER_DISTANCE;

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void blockoffensive$showDroppedC4OutlineForTOnly(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (!isDroppedC4Item(self)) {
            return;
        }

        cir.setReturnValue(canLocalPlayerSeeDroppedC4Outline());
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void blockoffensive$setDroppedC4OutlineColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (!isDroppedC4Item(self)) {
            return;
        }

        if (canLocalPlayerSeeDroppedC4Outline()) {
            cir.setReturnValue(T_C4_OUTLINE_COLOR);
        }
    }

    @Inject(method = "shouldRenderAtSqrDistance", at = @At("HEAD"), cancellable = true)
    private void blockoffensive$extendDroppedC4RenderDistance(double distanceSqr, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (!isDroppedC4Item(self)) {
            return;
        }

        if (canLocalPlayerSeeDroppedC4Outline()) {
            cir.setReturnValue(distanceSqr < C4_OUTLINE_RENDER_DISTANCE_SQR);
        }
    }

    private static boolean isDroppedC4Item(Entity entity) {
        if (!(entity instanceof ItemEntity itemEntity)) {
            return false;
        }
        ItemStack stack = itemEntity.getItem();
        return stack.is(BOItemRegister.C4.get());
    }

    private static boolean canLocalPlayerSeeDroppedC4Outline() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer localPlayer = minecraft.player;
        if (localPlayer == null) {
            return false;
        }

        FPSMClientGlobalData data = FPSMClient.getGlobalData();
        return data.isCurrentGameType("cs")
                && data.isInNormalTeam()
                && data.isCurrentTeam("t");
    }
}
