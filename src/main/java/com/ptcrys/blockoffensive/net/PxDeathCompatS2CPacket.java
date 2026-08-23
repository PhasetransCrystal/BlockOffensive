package com.ptcrys.blockoffensive.net;

import com.ptcrys.blockoffensive.compat.PhysicsModCompat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PxDeathCompatS2CPacket(int entityId) {

    public static void encode(PxDeathCompatS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.entityId);
    }

    public static PxDeathCompatS2CPacket decode(FriendlyByteBuf buf) {
        return new PxDeathCompatS2CPacket(buf.readInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            PhysicsModCompat.requestHandleDead(entityId);
        });
        ctx.get().setPacketHandled(true);
    }
}
