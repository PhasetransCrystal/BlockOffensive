package com.ptcrys.blockoffensive.mixin.client;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerInfo.class)
public interface PlayerInfoInvoker {
    @Invoker("setGameMode")
    void mcs2$setGameMode(GameType gameType);

    @Invoker("setLatency")
    void mcs2$setLatency(int latency);
}
