package net.ptcrys.blockoffensive.net;

import net.ptcrys.blockoffensive.BlockOffensive;
import net.ptcrys.blockoffensive.client.data.CSClientData;
import net.ptcrys.fpsmatch.common.client.FPSMClient;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Whole-match presentation snapshot sent by the server over the BO channel. */
public record CSScoreboardSync(String map, int[] rounds, int halfRounds,
                               int elapsedSeconds, int ctLoss, int tLoss) {

    public CSScoreboardSync {
        rounds = rounds.clone();
    }

    public static void send(ServerPlayer player, String map, int[] rounds, int halfRounds,
                            int elapsedSeconds, int ctLoss, int tLoss) {
        BlockOffensive.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new CSScoreboardSync(map, rounds, halfRounds, elapsedSeconds, ctLoss, tLoss));
    }

    public static void encode(CSScoreboardSync packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.map);
        buf.writeVarIntArray(packet.rounds);
        buf.writeVarInt(packet.halfRounds);
        buf.writeVarInt(packet.elapsedSeconds);
        buf.writeVarInt(packet.ctLoss);
        buf.writeVarInt(packet.tLoss);
    }

    public static CSScoreboardSync decode(FriendlyByteBuf buf) {
        return new CSScoreboardSync(buf.readUtf(), buf.readVarIntArray(4096), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> this::apply));
        context.setPacketHandled(true);
    }

    private void apply() {
        if (!FPSMClient.getGlobalData().isCurrentGameType("cs") || !map.equals(FPSMClient.getGlobalData().getCurrentMap())) return;
        CSClientData.scoreboardRounds = rounds.clone();
        CSClientData.scoreboardHalfRounds = Math.max(1, halfRounds);
        CSClientData.scoreboardElapsedSeconds = Math.max(0, elapsedSeconds);
        CSClientData.scoreboardCtLoss = Math.max(0, Math.min(5, ctLoss));
        CSClientData.scoreboardTLoss = Math.max(0, Math.min(5, tLoss));
    }
}
