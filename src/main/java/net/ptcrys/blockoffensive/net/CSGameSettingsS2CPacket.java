package net.ptcrys.blockoffensive.net;

import net.ptcrys.blockoffensive.client.data.CSClientData;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record CSGameSettingsS2CPacket(
                                      int cTWinnerRounds,
                                      int tWinnerRounds,
                                      int time,
                                      GameFlags flags) {

    public record GameFlags(byte value) {

        private static final byte DEBUG_FLAG = 1;
        private static final byte START_FLAG = 1 << 1;
        private static final byte ERROR_FLAG = 1 << 2;
        private static final byte PAUSE_FLAG = 1 << 3;
        private static final byte WAITING_FLAG = 1 << 4;
        private static final byte WAITING_WINNER_FLAG = 1 << 5;

        public static GameFlags of(boolean isDebug, boolean isStart, boolean isError,
                                   boolean isPause, boolean isWaiting, boolean isWaitingWinner) {
            byte flags = 0;
            if (isDebug) flags |= DEBUG_FLAG;
            if (isStart) flags |= START_FLAG;
            if (isError) flags |= ERROR_FLAG;
            if (isPause) flags |= PAUSE_FLAG;
            if (isWaiting) flags |= WAITING_FLAG;
            if (isWaitingWinner) flags |= WAITING_WINNER_FLAG;
            return new GameFlags(flags);
        }

        public boolean isDebug() {
            return (value & DEBUG_FLAG) != 0;
        }

        public boolean isStart() {
            return (value & START_FLAG) != 0;
        }

        public boolean isError() {
            return (value & ERROR_FLAG) != 0;
        }

        public boolean isPause() {
            return (value & PAUSE_FLAG) != 0;
        }

        public boolean isWaiting() {
            return (value & WAITING_FLAG) != 0;
        }

        public boolean isWaitingWinner() {
            return (value & WAITING_WINNER_FLAG) != 0;
        }
    }

    public static void encode(CSGameSettingsS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.cTWinnerRounds);
        buf.writeInt(packet.tWinnerRounds);
        buf.writeInt(packet.time);
        buf.writeByte(packet.flags.value());
    }

    public static CSGameSettingsS2CPacket decode(FriendlyByteBuf buf) {
        return new CSGameSettingsS2CPacket(
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                new GameFlags(buf.readByte()));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            CSClientData.cTWinnerRounds = this.cTWinnerRounds;
            CSClientData.tWinnerRounds = this.tWinnerRounds;
            CSClientData.time = this.time;
            CSClientData.isDebug = this.flags.isDebug();
            CSClientData.isStart = this.flags.isStart();
            CSClientData.isError = this.flags.isError();
            CSClientData.isPause = this.flags.isPause();
            CSClientData.isWaiting = this.flags.isWaiting();
            CSClientData.isWaitingWinner = this.flags.isWaitingWinner();
        });
        ctx.get().setPacketHandled(true);
    }
}
