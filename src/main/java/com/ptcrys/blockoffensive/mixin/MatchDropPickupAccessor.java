package com.ptcrys.blockoffensive.mixin;

import com.ptcrys.fpsmatch.common.entity.MatchDropEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = MatchDropEntity.class, remap = false)
public interface MatchDropPickupAccessor {
    @Accessor("pickupDelay") int blockoffensive$pickupDelay();
}
