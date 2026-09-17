package net.ptcrys.blockoffensive.net;

import net.ptcrys.fpsmatch.common.client.FPSMClient;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record CSTabRemovalS2CPacket(UUID uuid) {

    public static void encode(CSTabRemovalS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.uuid);
    }

    public static CSTabRemovalS2CPacket decode(FriendlyByteBuf buf) {
        return new CSTabRemovalS2CPacket(buf.readUUID());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            FPSMClient.getGlobalData().getTeamByUUID(uuid).ifPresent(team -> {
                team.delPlayer(uuid);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
