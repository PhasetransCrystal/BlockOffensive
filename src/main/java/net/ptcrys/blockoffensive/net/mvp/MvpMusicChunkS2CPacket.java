package net.ptcrys.blockoffensive.net.mvp;

import net.ptcrys.blockoffensive.client.mvp.MvpLocalMusicManager;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端：MVP 专属音乐分块分发（16KB/块）。
 * 客户端收齐后写入临时文件并播放（全场听到 MVP 玩家的专属音乐）。
 */
public class MvpMusicChunkS2CPacket {

    public static final int MAX_CHUNKS = 256;
    public static final int MAX_CHUNK_BYTES = 16 * 1024;
    public static final int MAX_TOTAL_BYTES = 4 * 1024 * 1024;

    private final UUID mvpId;
    private final int totalChunks;
    private final int chunkIndex;
    private final byte[] data;

    public MvpMusicChunkS2CPacket(UUID mvpId, int totalChunks, int chunkIndex, byte[] data) {
        this.mvpId = mvpId;
        this.totalChunks = totalChunks;
        this.chunkIndex = chunkIndex;
        this.data = data;
    }

    public static void encode(MvpMusicChunkS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.mvpId);
        buf.writeVarInt(msg.totalChunks);
        buf.writeVarInt(msg.chunkIndex);
        buf.writeByteArray(msg.data);
    }

    public static MvpMusicChunkS2CPacket decode(FriendlyByteBuf buf) {
        return new MvpMusicChunkS2CPacket(
                buf.readUUID(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readByteArray(MAX_CHUNK_BYTES));
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> MvpLocalMusicManager.onMusicChunk(mvpId, totalChunks, chunkIndex, data));
        ctx.setPacketHandled(true);
    }
}
