package com.ptcrys.blockoffensive.server.mvp;

import com.ptcrys.fpsmatch.FPSMatch;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 服务端 MVP 专属音乐存储（仅服务端）。
 * <p>
 * 玩家上传的专属音乐保存在 {@code config/blockoffensive/mvp_music/<玩家uuid>.ogg}，
 * MVP 时由服务端读取并分块广播给比赛中所有玩家。
 */
public final class MvpMusicServerStore {
    /** 单文件最大块数（16KB/块 × 256 = 4MB 上限）。 */
    public static final int MAX_CHUNKS = 256;
    /** 单块最大字节。 */
    public static final int MAX_CHUNK_BYTES = 16 * 1024;
    /** 单玩家音乐总字节上限（防磁盘填充）。 */
    public static final long MAX_TOTAL_BYTES = 4L * 1024 * 1024;

    private static final Path DIR = FMLPaths.CONFIGDIR.get().resolve("blockoffensive/mvp_music");
    private static final ConcurrentMap<UUID, UploadState> UPLOADS = new ConcurrentHashMap<>();

    private MvpMusicServerStore() {
    }

    /** 写入/追加一个上传块。index==0 时开启一次新的原子上传。 */
    public static boolean writeChunk(UUID player, int totalChunks, int chunkIndex, byte[] data) {
        if (player == null || data == null || data.length == 0
                || totalChunks <= 0 || totalChunks > MAX_CHUNKS
                || chunkIndex < 0 || chunkIndex >= totalChunks
                || data.length > MAX_CHUNK_BYTES) {
            return false;
        }

        try {
            Files.createDirectories(DIR);
            Path target = musicPath(player);
            Path temporary = temporaryPath(player);

            if (chunkIndex == 0) {
                UPLOADS.remove(player);
                Files.write(temporary, data);
                if (totalChunks == 1) {
                    moveIntoPlace(temporary, target);
                } else {
                    UPLOADS.put(player, new UploadState(totalChunks, 1, data.length, temporary));
                }
                return true;
            }

            UploadState state = UPLOADS.get(player);
            if (state == null || state.totalChunks != totalChunks
                    || state.nextChunk != chunkIndex
                    || state.totalBytes + data.length > MAX_TOTAL_BYTES) {
                return false;
            }

            Files.write(temporary, data, java.nio.file.StandardOpenOption.APPEND);
            if (chunkIndex == totalChunks - 1) {
                moveIntoPlace(temporary, target);
                UPLOADS.remove(player, state);
                return true;
            }
            UPLOADS.replace(player, state,
                    new UploadState(totalChunks, chunkIndex + 1,
                            state.totalBytes + data.length, temporary));
            return true;
        } catch (IOException e) {
            UPLOADS.remove(player);
            FPSMatch.LOGGER.error("[MvpMusic] failed to write chunk for {}", player, e);
            return false;
        }
    }

    /** 读取玩家专属音乐字节；不存在、为空或超过上限时返回 null。 */
    public static byte[] readAll(UUID player) {
        if (player == null) {
            return null;
        }
        try {
            Path file = musicPath(player);
            if (!Files.isRegularFile(file)) {
                return null;
            }
            long size = Files.size(file);
            if (size <= 0 || size > MAX_TOTAL_BYTES) {
                return null;
            }
            return Files.readAllBytes(file);
        } catch (IOException e) {
            FPSMatch.LOGGER.error("[MvpMusic] failed to read music for {}", player, e);
            return null;
        }
    }

    /** 玩家是否已上传专属音乐。 */
    public static boolean hasMusic(UUID player) {
        return readAll(player) != null;
    }

    /** 保存玩家专属音乐的显示名称（用于 MVP 横幅显示）。 */
    public static void saveName(UUID player, String displayName) {
        String safeName = sanitizeName(displayName);
        if (player == null || safeName == null) {
            return;
        }
        try {
            Files.createDirectories(DIR);
            Files.writeString(namePath(player), safeName,
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            FPSMatch.LOGGER.error("[MvpMusic] failed to save name for {}", player, e);
        }
    }

    /** 读取玩家专属音乐的显示名称；无则返回 null。 */
    public static String getName(UUID player) {
        if (player == null) {
            return null;
        }
        try {
            Path file = namePath(player);
            if (!Files.isRegularFile(file)) {
                return null;
            }
            String name = Files.readString(file, StandardCharsets.UTF_8);
            return sanitizeName(name);
        } catch (IOException e) {
            FPSMatch.LOGGER.error("[MvpMusic] failed to read name for {}", player, e);
            return null;
        }
    }

    private static String sanitizeName(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder result = new StringBuilder(Math.min(value.length(), 64));
        value.codePoints().forEach(codePoint -> {
            if (!Character.isISOControl(codePoint) && result.length() + Character.charCount(codePoint) <= 64) {
                result.appendCodePoint(codePoint);
            }
        });
        String safeName = result.toString().trim();
        return safeName.isEmpty() ? null : safeName;
    }

    private static Path musicPath(UUID player) {
        return DIR.resolve(player + ".ogg");
    }

    private static Path temporaryPath(UUID player) {
        return DIR.resolve(player + ".ogg.upload");
    }

    private static Path namePath(UUID player) {
        return DIR.resolve(player + ".name");
    }

    private static void moveIntoPlace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record UploadState(int totalChunks, int nextChunk, long totalBytes, Path temporary) {
    }
}
