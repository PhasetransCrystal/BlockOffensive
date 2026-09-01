package com.ptcrys.blockoffensive.client.mvp;

import com.google.gson.Gson;
import com.mojang.blaze3d.audio.OggAudioStream;
import com.mojang.blaze3d.audio.SoundBuffer;
import com.mojang.logging.LogUtils;
import com.ptcrys.blockoffensive.data.MvpReason;
import com.ptcrys.blockoffensive.net.mvp.MvpMusicChunkS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 本地 MVP 音乐管理器（仅客户端）：
 * <ul>
 *   <li>音乐存放于 {@code .minecraft/config/blockoffensive/mvp_music/}，固定文件名 {@code mvp_music.ogg}；</li>
 *   <li>收到 MVP 事件时判断：本机玩家是 MVP → 播放本地音乐；其他玩家 MVP → 静默（不干扰 HUD 动画）；</li>
 *   <li>播放使用 MC 内置 OggAudioStream + OpenAL（不引入播放库）；</li>
 *   <li>安装：魔数检测真实格式 → 时长校验（&gt;15 秒拒绝）→ OGG 直拷 / MP3/WAV 转码 → 更新元数据 JSON。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public final class MvpLocalMusicManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    /** 显示名称固定前缀。 */
    public static final String DISPLAY_PREFIX = "🎵 ";
    /** 时长上限（秒）：15~22 秒的音乐较多，放宽到 22 秒。 */
    public static final int MAX_DURATION_SECONDS = 22;

    private static final Path MUSIC_DIR = FMLPaths.CONFIGDIR.get().resolve("blockoffensive/mvp_music");
    private static final Path MUSIC_FILE = MUSIC_DIR.resolve("mvp_music.ogg");
    private static final Path META_FILE = MUSIC_DIR.resolve("mvp_music.json");

    private static MvpMusicData cachedData;
    /** 播放代次：每次 stop/新播放递增，用于让过期的 OpenAL 异步回调自行释放，防止 channel/buffer 错配泄漏。 */
    private static volatile long playGeneration = 0;
    private static volatile net.minecraft.client.sounds.ChannelAccess.ChannelHandle playingChannel;
    private static volatile SoundBuffer playingBuffer;

    private MvpLocalMusicManager() {
    }

    /** mvp_music.json 元数据。 */
    public record MvpMusicData(String displayName, String sourceFileName, int durationSeconds, long convertedAt, String format) {
    }

    // ================= 播放 =================

    /**
     * MVP 事件入口（由 MvpMessageS2CPacket 在主线程调用）。
     * <p>
     * 任何玩家成为 MVP 时等待服务端分发窗口；收到分块则播放分发音乐。
     * 只有在服务端未启用分发且本机玩家就是 MVP 时，才回退播放本地文件。
     */
    public static void onMvpEvent(MvpReason reason) {
        if (reason.uuid == null) {
            return;
        }
        pendingMvpId = reason.uuid;
        pendingMvpAt = System.currentTimeMillis();
    }

    /** 待判定 MVP：等待分发窗口。 */
    private static volatile UUID pendingMvpId;
    private static volatile long pendingMvpAt = 0;
    private static final long PENDING_WINDOW_MS = 1000L;

    // ================= 服务端分发接收 =================

    private static final java.util.Map<UUID, byte[][]> CHUNK_BUFFER = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<UUID, Long> CHUNK_LAST_UPDATE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CHUNK_TIMEOUT_MS = 30_000L;

    private static void purgeExpiredChunks() {
        long now = System.currentTimeMillis();
        CHUNK_LAST_UPDATE.entrySet().removeIf(e -> now - e.getValue() > CHUNK_TIMEOUT_MS);
        CHUNK_BUFFER.keySet().removeIf(uuid -> !CHUNK_LAST_UPDATE.containsKey(uuid));
    }

    /** 收到一个音乐分块：组装，收齐后写临时文件播放。 */
    public static void onMusicChunk(UUID mvpId, int totalChunks, int chunkIndex, byte[] data) {
        if (mvpId == null || !mvpId.equals(pendingMvpId)
                || data == null || data.length == 0
                || totalChunks <= 0 || totalChunks > MvpMusicChunkS2CPacket.MAX_CHUNKS
                || chunkIndex < 0 || chunkIndex >= totalChunks
                || data.length > MvpMusicChunkS2CPacket.MAX_CHUNK_BYTES) {
            return;
        }

        byte[][] chunks = CHUNK_BUFFER.compute(mvpId, (id, existing) -> {
            if (existing == null || existing.length != totalChunks) {
                return new byte[totalChunks][];
            }
            return existing;
        });
        chunks[chunkIndex] = data;
        CHUNK_LAST_UPDATE.put(mvpId, System.currentTimeMillis());
        for (byte[] chunk : chunks) {
            if (chunk == null) {
                return;
            }
        }

        long size = 0;
        for (byte[] chunk : chunks) {
            size += chunk.length;
        }
        if (size <= 0 || size > MvpMusicChunkS2CPacket.MAX_TOTAL_BYTES) {
            CHUNK_BUFFER.remove(mvpId);
            CHUNK_LAST_UPDATE.remove(mvpId);
            return;
        }

        CHUNK_BUFFER.remove(mvpId);
        CHUNK_LAST_UPDATE.remove(mvpId);
        pendingMvpId = null;
        byte[] all = new byte[(int) size];
        int offset = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, all, offset, chunk.length);
            offset += chunk.length;
        }
        try {
            Files.createDirectories(MUSIC_DIR);
            Path tmp = MUSIC_DIR.resolve("last_mvp_music.ogg");
            Files.write(tmp, all);
            playOggFile(tmp);
            LOGGER.info("[MvpMusic] played MVP player {} exclusive music ({} bytes)", mvpId, all.length);
        } catch (Exception e) {
            LOGGER.error("[MvpMusic] failed to write received music", e);
        }
    }

    /** 将本地 mvp_music.ogg 分块上传到服务器（MVP 时全场播放本机专属音乐），并携带显示名称。 */
    public static void uploadToServer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !Files.isRegularFile(MUSIC_FILE)) {
            return;
        }
        String displayName = getData() != null && getData().displayName() != null ? getData().displayName() : "";
        displayName = normalizeDisplayName(displayName);
        try {
            byte[] data = Files.readAllBytes(MUSIC_FILE);
            if (data.length == 0 || data.length > MvpMusicChunkS2CPacket.MAX_TOTAL_BYTES) {
                return;
            }
            int chunkSize = MvpMusicChunkS2CPacket.MAX_CHUNK_BYTES;
            int total = (data.length + chunkSize - 1) / chunkSize;
            for (int i = 0; i < total; i++) {
                int from = i * chunkSize;
                int to = Math.min(data.length, from + chunkSize);
                byte[] chunk = java.util.Arrays.copyOfRange(data, from, to);
                com.ptcrys.blockoffensive.BlockOffensive.INSTANCE.sendToServer(
                        new com.ptcrys.blockoffensive.net.mvp.MvpMusicUploadC2SPacket(
                                mc.player.getUUID(), total, i, chunk, displayName));
            }
            LOGGER.info("[MvpMusic] uploaded {} bytes in {} chunks (name='{}')", data.length, total, displayName);
        } catch (Exception e) {
            LOGGER.error("[MvpMusic] upload failed", e);
        }
    }

    /** 安装外部文件（本地转 OGG + 元数据）并上传服务器。返回 null 表示成功，否则错误消息。 */
    public static String installAndUpload(Path source) {
        String err = installMusic(source);
        if (err == null) {
            uploadToServer();
        }
        return err;
    }

    /** 播放本地 mvp_music.ogg（一次）。文件不存在则忽略。 */
    public static void playMvpMusic() {
        if (!Files.exists(MUSIC_FILE)) {
            return;
        }
        playOggFile(MUSIC_FILE);
    }

    private static void playOggFile(Path file) {
        stop();
        final long gen = ++playGeneration;
        try (InputStream in = Files.newInputStream(file)) {
            OggAudioStream ogg = new OggAudioStream(in);
            ByteBuffer pcm = ogg.readAll(); // 一次性解码为 PCM
            AudioFormat format = ogg.getFormat();
            ogg.close();

            SoundBuffer buffer = new SoundBuffer(pcm, format);
            net.minecraft.client.sounds.SoundEngine engine =
                    ((com.ptcrys.blockoffensive.mixin.client.SoundManagerAccessor)
                            (Object) Minecraft.getInstance().getSoundManager()).blockoffensive$getSoundEngine();
            net.minecraft.client.sounds.ChannelAccess channelAccess =
                    ((com.ptcrys.blockoffensive.mixin.client.SoundEngineChannelAccessor)
                            (Object) engine).blockoffensive$getChannelAccess();
            // 通过 SoundEngine 的官方通道分配（异步回调）
            channelAccess.createHandle(com.mojang.blaze3d.audio.Library.Pool.STATIC).thenAccept(handle -> {
                handle.execute(channel -> {
                    // 音量挂到 MC 设置"音乐"分类（SoundSource.MUSIC）：玩家可在
                    float vol = Minecraft.getInstance().options
                            .getSoundSourceVolume(net.minecraft.sounds.SoundSource.MUSIC);
                    channel.setVolume(vol);
                    channel.attachStaticBuffer(buffer);
                    channel.play();
                });
                if (gen != playGeneration) {
                    // 已被更新的播放/stop 取代：立即释放本次资源，防止错配泄漏
                    try {
                        handle.release();
                    } catch (Exception ignored) {
                    }
                    try {
                        buffer.releaseAlBuffer();
                    } catch (Exception ignored) {
                    }
                    return;
                }
                playingChannel = handle;
                playingBuffer = buffer;
                LOGGER.info("[MvpMusic] playing {}", file.getFileName());
            });
        } catch (Exception e) {
            LOGGER.error("[MvpMusic] failed to play {}", file.getFileName(), e);
        }
    }

    /** 试听当前设置的 MVP 音乐。 */
    public static void preview() {
        playMvpMusic();
    }

    /** 停止当前播放并释放资源。 */
    public static void stop() {
        playGeneration++; // 使在途异步回调过期
        if (playingChannel != null) {
            try {
                playingChannel.release();
            } catch (Exception ignored) {
            }
            playingChannel = null;
        }
        if (playingBuffer != null) {
            try {
                playingBuffer.releaseAlBuffer();
            } catch (Exception ignored) {
            }
            playingBuffer = null;
        }
    }

    /** 客户端 tick：播放结束后释放资源，防止泄漏；MVP 分发等待窗口超时后回退本地音乐。 */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (playingChannel != null && playingChannel.isStopped()) {
            stop();
        }
        if (pendingMvpId != null && System.currentTimeMillis() - pendingMvpAt > PENDING_WINDOW_MS) {
            UUID id = pendingMvpId;
            pendingMvpId = null;
            // 只有本机玩家是 MVP 时才回退本地音乐，避免把本机曲目错误地播放给其他玩家的 MVP。
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && id.equals(mc.player.getUUID())) {
                playMvpMusic();
            }
        }
    }

    // ================= 元数据 =================

    public static MvpMusicData getData() {
        if (cachedData == null) {
            cachedData = loadData();
        }
        return cachedData;
    }

    private static MvpMusicData loadData() {
        if (!Files.exists(META_FILE)) {
            return null;
        }
        try {
            return GSON.fromJson(Files.readString(META_FILE), MvpMusicData.class);
        } catch (Exception e) {
            LOGGER.warn("[MvpMusic] failed to read mvp_music.json", e);
            return null;
        }
    }

    public static void saveData(MvpMusicData data) {
        cachedData = data;
        try {
            Files.createDirectories(MUSIC_DIR);
            Files.writeString(META_FILE, GSON.toJson(data));
        } catch (Exception e) {
            LOGGER.error("[MvpMusic] failed to write mvp_music.json", e);
        }
    }

    // ================= 安装（选择文件后处理） =================

    /**
     * 安装所选音乐文件：检测格式 → 时长校验 → 复制/转码为 mvp_music.ogg → 更新元数据。
     *
     * @return null 表示成功；否则为面向玩家的错误提示。
     */
    public static String installMusic(Path source) {
        try {
            if (source == null || !Files.exists(source)) {
                return "文件不存在，请重新选择。";
            }
            MvpMusicConverter.Format format = MvpMusicConverter.detectFormat(source);
            if (format == null) {
                return "不支持的文件格式（仅支持 MP3 / OGG / WAV）。";
            }
            int duration = MvpMusicConverter.durationSeconds(source, format);
            if (duration > MAX_DURATION_SECONDS) {
                return "音乐超过 " + MAX_DURATION_SECONDS + " 秒，不予上传。请选择 "
                        + MAX_DURATION_SECONDS + " 秒以内的音乐文件。";
            }
            // 时长解析失败（duration<=0）不再当作损坏拒绝：继续处理，播放时由解码器容错

            Files.createDirectories(MUSIC_DIR);
            if (format == MvpMusicConverter.Format.OGG) {
                Files.copy(source, MUSIC_FILE, StandardCopyOption.REPLACE_EXISTING);
            } else {
                MvpMusicConverter.convertToOgg(source, format, MUSIC_FILE);
            }

            MvpMusicConverter.Meta meta = MvpMusicConverter.readMeta(source, format);
            String displayName = buildDisplayName(meta);
            saveData(new MvpMusicData(
                    displayName,
                    source.getFileName().toString(),
                    duration,
                    System.currentTimeMillis(),
                    "ogg"));
            return null;
        } catch (Exception e) {
            LOGGER.error("[MvpMusic] install failed", e);
            return "处理失败：" + e.getMessage();
        }
    }

    private static String normalizeDisplayName(String value) {
        StringBuilder result = new StringBuilder(Math.min(value.length(), 64));
        value.codePoints().forEach(codePoint -> {
            if (!Character.isISOControl(codePoint) && result.length() + Character.charCount(codePoint) <= 64) {
                result.appendCodePoint(codePoint);
            }
        });
        String normalized = result.toString().trim();
        return normalized.isEmpty() ? DISPLAY_PREFIX + "未命名 - 未知" : normalized;
    }

    /** 由元数据构建显示名称：🎵 标题 - 艺术家；元数据缺失时用占位。 */
    public static String buildDisplayName(MvpMusicConverter.Meta meta) {
        if (meta != null && meta.title() != null && !meta.title().isBlank()
                && meta.artist() != null && !meta.artist().isBlank()) {
            return DISPLAY_PREFIX + meta.title() + " - " + meta.artist();
        }
        return DISPLAY_PREFIX + "未命名 - 未知";
    }

    /** 校验显示名称格式：必须以 🎵 开头，且后跟 "歌曲名 - 作者名"。 */
    public static boolean isValidDisplayName(String name) {
        if (name == null || !name.startsWith(DISPLAY_PREFIX)) {
            return false;
        }
        String rest = name.substring(DISPLAY_PREFIX.length());
        return rest.contains(" - ");
    }

    public static Path getMusicDir() {
        return MUSIC_DIR;
    }

    /** 确保音乐目录存在（GUI 打开时调用，无需玩家手动创建）。 */
    public static void ensureMusicDir() {
        try {
            Files.createDirectories(MUSIC_DIR);
        } catch (Exception e) {
            LOGGER.warn("[MvpMusic] failed to create music dir {}", MUSIC_DIR, e);
        }
    }

    public static Path getMusicFile() {
        return MUSIC_FILE;
    }
}