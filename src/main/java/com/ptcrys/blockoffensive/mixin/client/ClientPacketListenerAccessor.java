package com.ptcrys.blockoffensive.mixin.client;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPacketListener.class)
public interface ClientPacketListenerAccessor {
    @Accessor("playerInfoMap")
    Map<UUID, PlayerInfo> mcs2$getPlayerInfoMap();

    @Accessor("listedPlayers")
    Set<PlayerInfo> mcs2$getListedPlayers();
}
