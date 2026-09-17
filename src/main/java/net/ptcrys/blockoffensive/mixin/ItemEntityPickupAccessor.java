package net.ptcrys.blockoffensive.mixin;

import net.minecraft.world.entity.item.ItemEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

@Mixin(ItemEntity.class)
public interface ItemEntityPickupAccessor {

    @Accessor("pickupDelay")
    int blockoffensive$pickupDelay();

    // Vanilla's target restricts pickup. The thrower is just attribution, not ownership.
    @Accessor("target")
    UUID blockoffensive$pickupTarget();
}
