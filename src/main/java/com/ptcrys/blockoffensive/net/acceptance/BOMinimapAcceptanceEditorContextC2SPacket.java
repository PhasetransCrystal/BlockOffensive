package com.ptcrys.blockoffensive.net.acceptance;

import com.ptcrys.blockoffensive.minimap.acceptance.BOMinimapAcceptanceServerBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record BOMinimapAcceptanceEditorContextC2SPacket(
        BOMinimapAcceptanceEditorContext editorContext
) {
    public static void encode(BOMinimapAcceptanceEditorContextC2SPacket packet, FriendlyByteBuf buf) {
        byte[] encoded = BOMinimapAcceptanceCodec.encodeEditorContext(packet.editorContext());
        if (encoded.length > BOMinimapAcceptanceCodec.MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("acceptance packet exceeds its hard limit");
        }
        buf.writeByteArray(encoded);
    }

    public static BOMinimapAcceptanceEditorContextC2SPacket decode(FriendlyByteBuf buf) {
        byte[] payload = buf.readByteArray(BOMinimapAcceptanceCodec.MAX_PACKET_BYTES);
        if (buf.isReadable()) {
            throw new IllegalArgumentException("acceptance editor context has trailing bytes");
        }
        return new BOMinimapAcceptanceEditorContextC2SPacket(
                BOMinimapAcceptanceCodec.decodeEditorContext(payload)
        );
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer sender = context.get().getSender();
            if (sender != null) {
                BOMinimapAcceptanceServerBridge.acceptEditorContext(sender, editorContext);
            }
        });
        context.get().setPacketHandled(true);
    }
}
