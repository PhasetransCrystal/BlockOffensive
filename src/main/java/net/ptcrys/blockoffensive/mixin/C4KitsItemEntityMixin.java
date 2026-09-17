package net.ptcrys.blockoffensive.mixin;

import net.ptcrys.blockoffensive.item.BOItemRegister;
import net.ptcrys.blockoffensive.item.BombDisposalKit;
import net.ptcrys.blockoffensive.item.CompositionC4;
import net.ptcrys.blockoffensive.map.CSGameMap;
import net.ptcrys.fpsmatch.core.FPSMCore;
import net.ptcrys.fpsmatch.core.map.BaseMap;
import net.ptcrys.fpsmatch.core.team.ServerTeam;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(ItemEntity.class)
public abstract class C4KitsItemEntityMixin {

    @Shadow
    public abstract ItemStack getItem();

    @Inject(at = @At("HEAD"), method = "playerTouch", cancellable = true)
    public void fpsMatch$playerTouch$CustomC4(Player player, CallbackInfo ci) {
        if (!player.isCreative() && !player.level().isClientSide) {
            if (this.getItem().getItem() instanceof CompositionC4) {
                Optional<BaseMap> optional = FPSMCore.getInstance().getMapByPlayer(player);
                if (optional.isEmpty()) {
                    ci.cancel();
                    return;
                }
                BaseMap map = optional.get();
                ServerTeam team = map.getMapTeams().getTeamByPlayer(player).orElse(null);
                if (team != null && map instanceof CSGameMap csGameMap) {
                    if (!csGameMap.checkCanPlacingBombs(team.getFixedName())) {
                        ci.cancel();
                    }
                } else {
                    ci.cancel();
                }
            }

            if (this.getItem().getItem() instanceof BombDisposalKit) {
                Optional<BaseMap> optional = FPSMCore.getInstance().getMapByPlayer(player);
                if (optional.isEmpty()) {
                    ci.cancel();
                    return;
                }
                BaseMap map = optional.get();
                ServerTeam team = map.getMapTeams().getTeamByPlayer(player).orElse(null);
                if (team != null && map instanceof CSGameMap csGameMap) {
                    if (!csGameMap.checkCanPlacingBombs(team.getFixedName())) {
                        int i = player.getInventory().countItem(BOItemRegister.BOMB_DISPOSAL_KIT.get());
                        if (i > 0) {
                            ci.cancel();
                        }
                    } else {
                        ci.cancel();
                    }
                } else {
                    ci.cancel();
                }
            }
        }
    }

    /**
     * After vanilla playerTouch proceeds, notify CS objective tracker of successful C4 pickup.
     * HEAD cancel paths never reach this inject.
     */
    @Inject(at = @At("RETURN"), method = "playerTouch")
    public void blockoffensive$playerTouch$NotifyC4Pickup(Player player, CallbackInfo ci) {
        if (player.level().isClientSide || player.isCreative()) {
            return;
        }
        if (!(this.getItem().getItem() instanceof CompositionC4)) {
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        // Item already collected by playerTouch when stack is empty / removed
        ItemEntity self = (ItemEntity) (Object) this;
        if (!self.isRemoved() && !this.getItem().isEmpty()) {
            return;
        }
    }
}
