package com.ptcrys.blockoffensive.net.acceptance;

import com.ptcrys.blockoffensive.minimap.acceptance.BOMinimapAcceptanceServerBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record BOMinimapAcceptanceAckC2SPacket(BOMinimapAcceptanceAck acknowledgement) {
    public static void encode(BOMinimapAcceptanceAckC2SPacket packet, FriendlyByteBuf buf) {
        byte[] encoded = BOMinimapAcceptanceCodec.encodeAck(packet.acknowledgement());
        if (encoded.length > BOMinimapAcceptanceCodec.MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("acceptance packet exceeds its hard limit");
        }
        buf.writeByteArray(encoded);
    }

    public static BOMinimapAcceptanceAckC2SPacket decode(FriendlyByteBuf buf) {
        byte[] payload = buf.readByteArray(BOMinimapAcceptanceCodec.MAX_PACKET_BYTES);
        if (buf.isReadable()) {
            throw new IllegalArgumentException("acceptance acknowledgement has trailing bytes");
        }
        return new BOMinimapAcceptanceAckC2SPacket(
                BOMinimapAcceptanceCodec.decodeAck(payload)
        );
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer sender = context.get().getSender();
            if (sender != null) {
                BOMinimapAcceptanceServerBridge.acceptAck(sender, acknowledgement);
            }
        });
        context.get().setPacketHandled(true);
    }
}
