package net.ptcrys.blockoffensive.mixin.client;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 访问 {@link SoundManager#soundEngine}（MC 私有字段）。
 */
@Mixin(SoundManager.class)
public interface SoundManagerAccessor {

    @Accessor("soundEngine")
    SoundEngine blockoffensive$getSoundEngine();
}
