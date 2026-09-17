package net.ptcrys.blockoffensive.net.dm;

import net.ptcrys.blockoffensive.map.CSDeathMatchMap;
import net.ptcrys.fpsmatch.core.FPSMCore;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PlayerMoveC2SPacket {

    public static void encode(PlayerMoveC2SPacket packet, FriendlyByteBuf buf) {}

    public static PlayerMoveC2SPacket decode(FriendlyByteBuf buf) {
        return new PlayerMoveC2SPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            FPSMCore.getInstance().getMapByPlayer(player).ifPresent(map -> {
                if (map instanceof CSDeathMatchMap dm) {
                    dm.handlePlayerMove(player.getUUID());
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
