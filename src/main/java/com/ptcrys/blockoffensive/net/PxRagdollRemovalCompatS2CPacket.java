package com.ptcrys.blockoffensive.net;

import com.ptcrys.blockoffensive.compat.PhysicsModCompat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record PxRagdollRemovalCompatS2CPacket(UUID uuid) {
    public static final UUID ALL = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public static void encode(PxRagdollRemovalCompatS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.uuid);
    }

    public static PxRagdollRemovalCompatS2CPacket decode(FriendlyByteBuf buf) {
        return new PxRagdollRemovalCompatS2CPacket(buf.readUUID());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (uuid.equals(ALL)) {
                PhysicsModCompat.reset();
            } else {
                PhysicsModCompat.BORagdollHook.INSTANCE.remove(uuid);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
