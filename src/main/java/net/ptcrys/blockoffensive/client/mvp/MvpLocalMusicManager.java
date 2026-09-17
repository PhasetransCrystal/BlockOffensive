package net.ptcrys.blockoffensive.client.mvp;

import net.ptcrys.blockoffensive.data.MvpReason;
import net.ptcrys.blockoffensive.net.mvp.MvpMusicChunkS2CPacket;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import com.google.gson.Gson;
import com.mojang.blaze3d.audio.OggAudioStream;
import com.mojang.blaze3d.audio.SoundBuffer;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sound.sampled.AudioFormat;

/**
 * 本地 MVP 音乐管理器（仅客户端）：
 * 音乐存放于 {@code .minecraft/config/blockoffensive/mvp_music/}，固定文件名 {@code mvp_music.ogg}；
 * 收到 MVP 事件时判断：本机玩家是 MVP → 播放本地音乐；其他玩家 MVP → 静默（不干扰 HUD 动画）；
 * 播放使用 MC 内置 OggAudioStream + OpenAL（不引入播放库）；
 * 安装：魔数检测真实格式 → 时长校验（&gt;15 秒拒绝）→ OGG 直拷 / MP3/WAV 转码 → 更新元数据 JSON。
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
    private static volatile PlaybackState currentPlayback;
    private static final ThreadPoolExecutor AUDIO_EXECUTOR = new ThreadPoolExecutor(
            1, 1, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), runnable -> {
                Thread thread = new Thread(runnable, "BlockOffensive-MvpMusic");
                thread.setDaemon(true);
                return thread;
            });

    private static final class PlaybackState {

        private final long generation;
        private final SoundBuffer buffer;
        private final AtomicBoolean bufferReleased = new AtomicBoolean();
        private volatile net.minecraft.client.sounds.ChannelAccess.ChannelHandle handle;
        private volatile boolean canceled;

        private PlaybackState(long generation, SoundBuffer buffer) {
            this.generation = generation;
            this.buffer = buffer;
        }

        private void releaseBuffer() {
            if (bufferReleased.compareAndSet(false, true)) {
                try {
                    buffer.releaseAlBuffer();
                } catch (Exception ignored) {}
            }
        }
    }

    private MvpLocalMusicManager() {}

    /** mvp_music.json 元数据。 */
    public record MvpMusicData(String displayName, String sourceFileName, int durationSeconds, long convertedAt, String format) {}

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
        if (mvpId == null || !mvpId.equals(pendingMvpId) || data == null || data.length == 0 || totalChunks <= 0 || totalChunks > MvpMusicChunkS2CPacket.MAX_CHUNKS || chunkIndex < 0 || chunkIndex >= totalChunks || data.length > MvpMusicChunkS2CPacket.MAX_CHUNK_BYTES) {
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
        byte[] received = all;
        AUDIO_EXECUTOR.execute(() -> {
            try {
                Files.createDirectories(MUSIC_DIR);
                Path tmp = Files.createTempFile(MUSIC_DIR, "received-", ".ogg");
                Files.write(tmp, received);
                Minecraft.getInstance().execute(() -> playOggFile(tmp));
                LOGGER.info("[MvpMusic] received MVP player {} exclusive music ({} bytes)", mvpId, received.length);
            } catch (Exception e) {
                LOGGER.error("[MvpMusic] failed to write received music", e);
            }
        });
    }

    /** 将本地 mvp_music.ogg 分块上传到服务器（MVP 时全场播放本机专属音乐），并携带显示名称。 */
    public static void uploadToServer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !Files.isRegularFile(MUSIC_FILE)) {
            return;
        }
        UUID playerId = mc.player.getUUID();
        String displayName = getData() != null && getData().displayName() != null ? getData().displayName() : "";
        String uploadName = normalizeDisplayName(displayName);
        AUDIO_EXECUTOR.execute(() -> {
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
                    net.ptcrys.blockoffensive.BlockOffensive.INSTANCE.sendToServer(
                            new net.ptcrys.blockoffensive.net.mvp.MvpMusicUploadC2SPacket(
                                    playerId, total, i, chunk, uploadName));
                }
                LOGGER.info("[MvpMusic] uploaded {} bytes in {} chunks (name='{}')", data.length, total, uploadName);
            } catch (Exception e) {
                LOGGER.error("[MvpMusic] upload failed", e);
            }
        });
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
        final long generation = ++playGeneration;
        final boolean temporaryFile = file.getFileName().toString().startsWith("received-");
        AUDIO_EXECUTOR.execute(() -> {
            try (InputStream in = Files.newInputStream(file);
                    OggAudioStream ogg = new OggAudioStream(in)) {
                ByteBuffer pcm = ogg.readAll();
                AudioFormat format = ogg.getFormat();
                Minecraft.getInstance().execute(() -> startPlayback(file, generation, pcm, format));
            } catch (Exception e) {
                LOGGER.error("[MvpMusic] failed to decode {}", file, e);
            } finally {
                if (temporaryFile) {
                    try {
                        Files.deleteIfExists(file);
                    } catch (Exception e) {
                        LOGGER.warn("[MvpMusic] failed to delete temporary file {}", file, e);
                    }
                }
            }
        });
    }

    private static void startPlayback(Path file, long generation, ByteBuffer pcm, AudioFormat format) {
        if (generation != playGeneration) {
            return;
        }
        SoundBuffer buffer = new SoundBuffer(pcm, format);
        PlaybackState state = new PlaybackState(generation, buffer);
        currentPlayback = state;
        try {
            net.minecraft.client.sounds.SoundEngine engine = ((net.ptcrys.blockoffensive.mixin.client.SoundManagerAccessor) (Object) Minecraft.getInstance().getSoundManager()).blockoffensive$getSoundEngine();
            net.minecraft.client.sounds.ChannelAccess channelAccess = ((net.ptcrys.blockoffensive.mixin.client.SoundEngineChannelAccessor) (Object) engine).blockoffensive$getChannelAccess();
            channelAccess.createHandle(com.mojang.blaze3d.audio.Library.Pool.STATIC).thenAccept(handle -> {
                state.handle = handle;
                if (state.generation != playGeneration || state.canceled || currentPlayback != state) {
                    state.releaseBuffer();
                    handle.release();
                    return;
                }
                try {
                    handle.execute(channel -> {
                        if (state.generation != playGeneration || state.canceled || currentPlayback != state) {
                            channel.stop();
                            state.releaseBuffer();
                            return;
                        }
                        float volume = Minecraft.getInstance().options
                                .getSoundSourceVolume(net.minecraft.sounds.SoundSource.MUSIC);
                        channel.setVolume(volume);
                        channel.attachStaticBuffer(buffer);
                        channel.play();
                    });
                } catch (Exception e) {
                    if (currentPlayback == state) {
                        currentPlayback = null;
                    }
                    state.releaseBuffer();
                    try {
                        handle.release();
                    } catch (Exception ignored) {}
                    return;
                }
                LOGGER.info("[MvpMusic] playing {}", file.getFileName());
            });
        } catch (Exception e) {
            currentPlayback = null;
            state.releaseBuffer();
            LOGGER.error("[MvpMusic] failed to start {}", file, e);
        }
    }

    /** 试听当前设置的 MVP 音乐。 */
    public static void preview() {
        playMvpMusic();
    }

    /** 停止当前播放并释放资源。 */
    public static void stop() {
        playGeneration++;
        PlaybackState state = currentPlayback;
        currentPlayback = null;
        if (state == null) {
            return;
        }
        state.canceled = true;
        net.minecraft.client.sounds.ChannelAccess.ChannelHandle handle = state.handle;
        if (handle != null) {
            try {
                handle.execute(channel -> {
                    channel.stop();
                    state.releaseBuffer();
                });
            } catch (Exception e) {
                state.releaseBuffer();
            }
            try {
                handle.release();
            } catch (Exception ignored) {}
        } else {
            state.releaseBuffer();
        }
    }

    /** 客户端 tick：播放结束后释放资源，防止泄漏；MVP 分发等待窗口超时后回退本地音乐。 */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        PlaybackState playback = currentPlayback;
        if (playback != null && playback.handle != null && playback.handle.isStopped()) {
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
        try {
            Files.createDirectories(MUSIC_DIR);
            Path tmp = META_FILE.resolveSibling("mvp_music.json.tmp");
            Files.writeString(tmp, GSON.toJson(data));
            try {
                Files.move(tmp, META_FILE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, META_FILE, StandardCopyOption.REPLACE_EXISTING);
            }
            cachedData = data;
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
                return "音乐超过 " + MAX_DURATION_SECONDS + " 秒，不予上传。请选择 " + MAX_DURATION_SECONDS + " 秒以内的音乐文件。";
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
        if (meta != null && meta.title() != null && !meta.title().isBlank() && meta.artist() != null && !meta.artist().isBlank()) {
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
