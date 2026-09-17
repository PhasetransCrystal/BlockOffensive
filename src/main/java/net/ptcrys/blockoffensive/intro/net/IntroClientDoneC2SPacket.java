package net.ptcrys.blockoffensive.intro.net;

import net.ptcrys.blockoffensive.intro.IntroRuntimeController;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;

public class IntroClientDoneC2SPacket {

    private static final int MAX_REASON_LENGTH = 128;

    private final UUID sequenceId;
    private final String reason;

    public IntroClientDoneC2SPacket(UUID sequenceId, String reason) {
        this.sequenceId = sequenceId;
        this.reason = reason == null ? "done" : reason;
    }

    public UUID sequenceId() {
        return sequenceId;
    }

    public String reason() {
        return reason;
    }

    public static IntroClientDoneC2SPacket decode(FriendlyByteBuf buf) {
        return new IntroClientDoneC2SPacket(buf.readUUID(), buf.readUtf(MAX_REASON_LENGTH));
    }

    public static void encode(IntroClientDoneC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.sequenceId);
        buf.writeUtf(packet.reason, MAX_REASON_LENGTH);
    }

    public static void handle(IntroClientDoneC2SPacket packet, java.util.function.Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> IntroRuntimeController.onClientDone(ctx.getSender(), packet));
        ctx.setPacketHandled(true);
    }

    public void handle(java.util.function.Supplier<NetworkEvent.Context> context) {
        handle(this, context);
    }
}
