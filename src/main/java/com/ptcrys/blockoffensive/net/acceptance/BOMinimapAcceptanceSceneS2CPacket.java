package com.ptcrys.blockoffensive.net.acceptance;

import com.ptcrys.blockoffensive.minimap.acceptance.BOMinimapAcceptanceClientEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record BOMinimapAcceptanceSceneS2CPacket(BOMinimapAcceptanceSceneSignal signal) {
    public static void encode(BOMinimapAcceptanceSceneS2CPacket packet, FriendlyByteBuf buf) {
        byte[] encoded = BOMinimapAcceptanceCodec.encodeScene(packet.signal());
        if (encoded.length > BOMinimapAcceptanceCodec.MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("acceptance packet exceeds its hard limit");
        }
        buf.writeByteArray(encoded);
    }

    public static BOMinimapAcceptanceSceneS2CPacket decode(FriendlyByteBuf buf) {
        byte[] payload = buf.readByteArray(BOMinimapAcceptanceCodec.MAX_PACKET_BYTES);
        if (buf.isReadable()) {
            throw new IllegalArgumentException("acceptance scene packet has trailing bytes");
        }
        return new BOMinimapAcceptanceSceneS2CPacket(
                BOMinimapAcceptanceCodec.decodeScene(payload)
        );
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context networkContext = context.get();
        BOMinimapAcceptanceClientEvents.enqueue(signal, networkContext);
        networkContext.setPacketHandled(true);
    }
}
