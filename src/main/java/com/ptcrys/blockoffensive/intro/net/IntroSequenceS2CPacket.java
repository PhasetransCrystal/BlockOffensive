package com.ptcrys.blockoffensive.intro.net;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.ptcrys.blockoffensive.intro.IntroPhase;
import com.ptcrys.blockoffensive.intro.IntroSequence;
import com.ptcrys.blockoffensive.intro.IntroTeamSide;
import com.ptcrys.blockoffensive.intro.client.IntroClientController;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public class IntroSequenceS2CPacket {
    private static final int MAX_MAP_LENGTH = 128;
    private static final int MAX_ITEM_ID_LENGTH = 128;
    private static final int MAX_PLAYERS = 10;

    public final String gameType;
    public final UUID sequenceId;
    public final long startGameTime;
    public final String mapName;
    public final IntroPhase phase;
    public final IntroTeamSide side;
    public final int durationTicks;
    public final int preRollTicks;
    public final int cinematicReadyAtTick;
    public final String previewItemId;
    public final Vec3 cameraStart;
    public final Vec3 cameraEnd;
    public final float cameraStartYaw;
    public final float cameraStartPitch;
    public final float cameraEndYaw;
    public final float cameraEndPitch;
    public final List<IntroSequence.PlayerPath> players;
    public final List<ItemStack> heldItems;

    public IntroSequenceS2CPacket(IntroSequence sequence) {
        this(UUID.randomUUID(), sequence);
    }

    public IntroSequenceS2CPacket(UUID sequenceId, IntroSequence sequence) {
        this(
                sequenceId,
                sequence.gameType(),
                sequence.mapName(),
                sequence.phase(),
                sequence.side(),
                sequence.durationTicks(),
                sequence.preRollTicks(),
                sequence.cinematicReadyAtTick(),
                sequence.safePreviewItemId(),
                sequence.cameraStart(),
                sequence.cameraEnd(),
                sequence.cameraStartYaw(),
                sequence.cameraStartPitch(),
                sequence.cameraEndYaw(),
                sequence.cameraEndPitch(),
                sequence.players(),
                List.of(), -1
        );
    }

    public IntroSequenceS2CPacket(UUID sequenceId, IntroSequence sequence, List<ItemStack> heldItems) {
        this(
                sequenceId,
                sequence.gameType(),
                sequence.mapName(),
                sequence.phase(),
                sequence.side(),
                sequence.durationTicks(),
                sequence.preRollTicks(),
                sequence.cinematicReadyAtTick(),
                sequence.safePreviewItemId(),
                sequence.cameraStart(),
                sequence.cameraEnd(),
                sequence.cameraStartYaw(),
                sequence.cameraStartPitch(),
                sequence.cameraEndYaw(),
                sequence.cameraEndPitch(),
                sequence.players(),
                heldItems, -1
        );
    }

    public IntroSequenceS2CPacket(
            UUID sequenceId,
            String gameType,
            String mapName,
            IntroPhase phase,
            IntroTeamSide side,
            int durationTicks,
            int preRollTicks,
            int cinematicReadyAtTick,
            String previewItemId,
            Vec3 cameraStart,
            Vec3 cameraEnd,
            float cameraStartYaw,
            float cameraStartPitch,
            float cameraEndYaw,
            float cameraEndPitch,
            List<IntroSequence.PlayerPath> players,
            List<ItemStack> heldItems,
            long startGameTime
    ) {
        this.sequenceId = sequenceId;
        this.startGameTime = startGameTime;
        this.gameType = gameType;
        this.mapName = mapName;
        this.phase = phase;
        this.side = side;
        this.durationTicks = durationTicks;
        this.preRollTicks = Math.max(0, preRollTicks);
        this.cinematicReadyAtTick = Math.max(0, cinematicReadyAtTick);
        this.previewItemId = previewItemId == null ? "" : previewItemId;
        this.cameraStart = cameraStart;
        this.cameraEnd = cameraEnd;
        this.cameraStartYaw = cameraStartYaw;
        this.cameraStartPitch = cameraStartPitch;
        this.cameraEndYaw = cameraEndYaw;
        this.cameraEndPitch = cameraEndPitch;
        if (players.size() > MAX_PLAYERS) {
            throw new IllegalArgumentException("Too many intro players: " + players.size());
        }
        this.players = List.copyOf(players);
        List<ItemStack> safeHeldItems = heldItems == null ? List.of() : heldItems;
        ArrayList<ItemStack> copiedHeldItems = new ArrayList<>();
        for (int i = 0; i < this.players.size(); i++) {
            ItemStack stack = i < safeHeldItems.size() ? safeHeldItems.get(i) : ItemStack.EMPTY;
            copiedHeldItems.add(stack == null ? ItemStack.EMPTY : stack.copy());
        }
        this.heldItems = List.copyOf(copiedHeldItems);
    }

    public IntroSequenceS2CPacket startingAt(long gameTime) {
        return new IntroSequenceS2CPacket(sequenceId, gameType, mapName, phase, side, durationTicks,
                preRollTicks, cinematicReadyAtTick, previewItemId, cameraStart, cameraEnd,
                cameraStartYaw, cameraStartPitch, cameraEndYaw, cameraEndPitch, players, heldItems, gameTime);
    }

    public static IntroSequenceS2CPacket decode(FriendlyByteBuf buf) {
        UUID sequenceId = buf.readUUID();
        String gameType = buf.readUtf(MAX_MAP_LENGTH);
        String mapName = buf.readUtf(MAX_MAP_LENGTH);
        IntroPhase phase = buf.readEnum(IntroPhase.class);
        IntroTeamSide side = buf.readEnum(IntroTeamSide.class);
        int duration = buf.readVarInt();
        int preRoll = buf.readVarInt();
        int cinematicReadyAtTick = buf.readVarInt();
        String previewItemId = buf.readUtf(MAX_ITEM_ID_LENGTH);
        Vec3 cameraStart = readVec3(buf);
        Vec3 cameraEnd = readVec3(buf);
        float cameraStartYaw = buf.readFloat();
        float cameraStartPitch = buf.readFloat();
        float cameraEndYaw = buf.readFloat();
        float cameraEndPitch = buf.readFloat();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_PLAYERS) {
            throw new IllegalArgumentException("Invalid intro player count: " + count);
        }
        ArrayList<IntroSequence.PlayerPath> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID id = buf.readUUID();
            Vec3 start = readVec3(buf);
            Vec3 end = readVec3(buf);
            Vec3 finalSpawn = readVec3(buf);
            float yaw = buf.readFloat();
            float pitch = buf.readFloat();
            players.add(new IntroSequence.PlayerPath(id, start, end, finalSpawn, yaw, pitch));
        }
        ArrayList<ItemStack> heldItems = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            heldItems.add(buf.readItem());
        }
        long startGameTime = buf.readLong();
        return new IntroSequenceS2CPacket(sequenceId, gameType, mapName, phase, side, duration, preRoll, cinematicReadyAtTick, previewItemId, cameraStart, cameraEnd, cameraStartYaw, cameraStartPitch, cameraEndYaw, cameraEndPitch, players, heldItems, startGameTime);
    }

    public static void encode(IntroSequenceS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.sequenceId);
        buf.writeUtf(packet.gameType, MAX_MAP_LENGTH);
        buf.writeUtf(packet.mapName, MAX_MAP_LENGTH);
        buf.writeEnum(packet.phase);
        buf.writeEnum(packet.side);
        buf.writeVarInt(packet.durationTicks);
        buf.writeVarInt(packet.preRollTicks);
        buf.writeVarInt(packet.cinematicReadyAtTick);
        buf.writeUtf(packet.previewItemId, MAX_ITEM_ID_LENGTH);
        writeVec3(buf, packet.cameraStart);
        writeVec3(buf, packet.cameraEnd);
        buf.writeFloat(packet.cameraStartYaw);
        buf.writeFloat(packet.cameraStartPitch);
        buf.writeFloat(packet.cameraEndYaw);
        buf.writeFloat(packet.cameraEndPitch);
        if (packet.players.size() > MAX_PLAYERS) {
            throw new IllegalArgumentException("Too many intro players: " + packet.players.size());
        }
        buf.writeVarInt(packet.players.size());
        for (IntroSequence.PlayerPath path : packet.players) {
            buf.writeUUID(path.playerId());
            writeVec3(buf, path.start());
            writeVec3(buf, path.end());
            writeVec3(buf, path.finalSpawn());
            buf.writeFloat(path.yaw());
            buf.writeFloat(path.pitch());
        }
        for (int i = 0; i < packet.players.size(); i++) {
            ItemStack stack = i < packet.heldItems.size() ? packet.heldItems.get(i) : ItemStack.EMPTY;
            buf.writeItem(stack == null ? ItemStack.EMPTY : stack);
        }
        buf.writeLong(packet.startGameTime);
    }

    public static void handle(IntroSequenceS2CPacket packet, java.util.function.Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> IntroClientController.accept(packet)));
        ctx.setPacketHandled(true);
    }

    public void handle(java.util.function.Supplier<NetworkEvent.Context> context) {
        handle(this, context);
    }

    public int cinematicReadyAtTick() {
        return cinematicReadyAtTick;
    }

    public int movementStartTick() {
        return Math.max(preRollTicks, cinematicReadyAtTick);
    }

    private static Vec3 readVec3(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private static void writeVec3(FriendlyByteBuf buf, Vec3 vec) {
        buf.writeDouble(vec.x);
        buf.writeDouble(vec.y);
        buf.writeDouble(vec.z);
    }
}
