package net.ptcrys.blockoffensive.net;

import net.ptcrys.blockoffensive.client.data.CSClientData;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class CSScoreboardS2CPacket {

    private final String map;
    private final int[] rounds;
    private final int halfRounds, elapsedSeconds, ctLoss, tLoss;

    public CSScoreboardS2CPacket(String map, int[] rounds, int halfRounds, int elapsedSeconds, int ctLoss, int tLoss) {
        this.map = map;
        this.rounds = rounds.clone();
        this.halfRounds = halfRounds;
        this.elapsedSeconds = elapsedSeconds;
        this.ctLoss = ctLoss;
        this.tLoss = tLoss;
    }

    public static void encode(CSScoreboardS2CPacket p, FriendlyByteBuf b) {
        b.writeUtf(p.map, 128);
        b.writeVarIntArray(p.rounds);
        b.writeInt(p.halfRounds);
        b.writeInt(p.elapsedSeconds);
        b.writeInt(p.ctLoss);
        b.writeInt(p.tLoss);
    }

    public static CSScoreboardS2CPacket decode(FriendlyByteBuf b) {
        return new CSScoreboardS2CPacket(b.readUtf(128), b.readVarIntArray(64), b.readInt(), b.readInt(), b.readInt(), b.readInt());
    }

    public void handle(Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> {
            CSClientData.scoreboardRounds = rounds.clone();
            CSClientData.scoreboardHalfRounds = Math.max(1, halfRounds);
            CSClientData.scoreboardElapsedSeconds = Math.max(0, elapsedSeconds);
            CSClientData.scoreboardCtLoss = Math.max(0, Math.min(5, ctLoss));
            CSClientData.scoreboardTLoss = Math.max(0, Math.min(5, tLoss));
        });
        c.get().setPacketHandled(true);
    }
}
