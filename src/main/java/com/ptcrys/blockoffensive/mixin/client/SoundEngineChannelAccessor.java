package com.ptcrys.blockoffensive.mixin.client;

import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 访问 {@link SoundEngine#channelAccess}（MC 私有字段），用于播放本地 OGG 音乐。
 */
@Mixin(SoundEngine.class)
public interface SoundEngineChannelAccessor {
    @Accessor("channelAccess")
    ChannelAccess blockoffensive$getChannelAccess();
}