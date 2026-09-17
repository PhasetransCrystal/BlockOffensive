package net.ptcrys.blockoffensive.net.mvp;

import net.ptcrys.blockoffensive.server.mvp.MvpMusicServerStore;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端：MVP 专属音乐分块上传（16KB/块）。
 * 服务端按玩家 UUID 追加写入 {@code config/blockoffensive/mvp_music/<uuid>.ogg}。
 */
public class MvpMusicUploadC2SPacket {

    private static final int MAX_DISPLAY_NAME_LENGTH = 64;

    private final UUID playerId;
    private final int totalChunks;
    private final int chunkIndex;
    private final byte[] data;
    /** 音乐显示名称（随上传一并保存，用于 MVP 横幅显示）。 */
    private final String displayName;

    public MvpMusicUploadC2SPacket(UUID playerId, int totalChunks, int chunkIndex, byte[] data, String displayName) {
        this.playerId = playerId;
        this.totalChunks = totalChunks;
        this.chunkIndex = chunkIndex;
        this.data = data;
        this.displayName = displayName == null ? "" : displayName;
    }

    public static void encode(MvpMusicUploadC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerId);
        buf.writeVarInt(msg.totalChunks);
        buf.writeVarInt(msg.chunkIndex);
        buf.writeByteArray(msg.data);
        buf.writeUtf(msg.displayName, MAX_DISPLAY_NAME_LENGTH);
    }

    public static MvpMusicUploadC2SPacket decode(FriendlyByteBuf buf) {
        return new MvpMusicUploadC2SPacket(
                buf.readUUID(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readByteArray(MvpMusicChunkS2CPacket.MAX_CHUNK_BYTES),
                buf.readUtf(MAX_DISPLAY_NAME_LENGTH));
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sp = ctx.getSender();
            if (sp == null || !sp.getUUID().equals(playerId)) {
                return; // 只能上传自己的音乐
            }
            if (!isValidChunk()) {
                return;
            }
            boolean accepted = MvpMusicServerStore.writeChunk(playerId, totalChunks, chunkIndex, data);
            if (accepted && chunkIndex == totalChunks - 1 && isValidDisplayName()) {
                MvpMusicServerStore.saveName(playerId, displayName.trim());
            }
        });
        ctx.setPacketHandled(true);
    }

    private boolean isValidChunk() {
        return data != null && data.length > 0 && totalChunks > 0 && totalChunks <= MvpMusicServerStore.MAX_CHUNKS && chunkIndex >= 0 && chunkIndex < totalChunks && data.length <= MvpMusicServerStore.MAX_CHUNK_BYTES;
    }

    private boolean isValidDisplayName() {
        return !displayName.isBlank() && displayName.length() <= MAX_DISPLAY_NAME_LENGTH && displayName.codePoints().noneMatch(Character::isISOControl);
    }
}
