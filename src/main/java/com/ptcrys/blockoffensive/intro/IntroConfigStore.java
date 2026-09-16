package com.ptcrys.blockoffensive.intro;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

public final class IntroConfigStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "blockoffensive-halftime-intro.json";

    private static final Map<String, MapConfig> MAPS = new LinkedHashMap<>();
    private static Path loadedPath;

    private IntroConfigStore() {
    }

    public static synchronized void load(MinecraftServer server) {
        Path path = resolvePath(server);
        loadedPath = path;
        MAPS.clear();
        if (!Files.exists(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            Root root = GSON.fromJson(reader, Root.class);
            if (root != null && root.maps != null) {
                MAPS.putAll(root.maps);
            }
        } catch (IOException | JsonParseException e) {
            LOGGER.warn("[BlockOffensive Halftime] Failed to load {}", path, e);
        }
    }

    public static synchronized void save(MinecraftServer server) {
        Path path = loadedPath != null ? loadedPath : resolvePath(server);
        try {
            Files.createDirectories(path.getParent());
            Root root = new Root();
            root.maps.putAll(MAPS);
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("[BlockOffensive Halftime] Failed to save {}", path, e);
        }
    }

    public static synchronized Optional<SideConfig> get(String gameType, String mapName, IntroTeamSide side) {
        MapConfig map = MAPS.get(key(gameType, mapName));
        if (map == null) {
            return Optional.empty();
        }
        SideConfig config = side == IntroTeamSide.CT ? map.ct : map.t;
        return Optional.ofNullable(config).filter(SideConfig::hasArea);
    }

    public static synchronized SideConfig getOrCreate(String gameType, String mapName, IntroTeamSide side) {
        MapConfig map = MAPS.computeIfAbsent(key(gameType, mapName), ignored -> new MapConfig());
        if (side == IntroTeamSide.CT) {
            if (map.ct == null) {
                map.ct = new SideConfig();
            }
            return map.ct;
        }
        if (map.t == null) {
            map.t = new SideConfig();
        }
        return map.t;
    }

    public static synchronized boolean clear(String gameType, String mapName, IntroTeamSide side) {
        MapConfig map = MAPS.get(key(gameType, mapName));
        if (map == null) {
            return false;
        }
        if (side == IntroTeamSide.CT) {
            boolean existed = map.ct != null;
            map.ct = null;
            return existed;
        }
        boolean existed = map.t != null;
        map.t = null;
        return existed;
    }

    public static String key(String gameType, String mapName) {
        String gt = gameType == null || gameType.isBlank() ? "cs" : gameType;
        return gt + ":" + Objects.requireNonNullElse(mapName, "");
    }

    public static Path currentPath(MinecraftServer server) {
        return loadedPath != null ? loadedPath : resolvePath(server);
    }

    private static Path resolvePath(MinecraftServer server) {
        if (server != null) {
            return server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve(FILE_NAME);
        }
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    private static final class Root {
        Map<String, MapConfig> maps = new LinkedHashMap<>();
    }

    public static final class MapConfig {
        public SideConfig ct;
        public SideConfig t;
    }

    public static final class SideConfig {
        public AreaBox area;
        public float yaw;
        public float pitch;
        public int durationTicks = 90;
        public boolean startEnabled = false;
        public boolean switchEnabled = true;

        public boolean hasArea() {
            return area != null && area.isValid();
        }

        public boolean isEnabled(IntroPhase phase) {
            return switch (phase) {
                case START -> startEnabled;
                case SWITCH -> switchEnabled;
                case PREVIEW, PREVIEW5, PREARM -> true;
                case STOP -> false;
            };
        }

        public int clampedDurationTicks() {
            return Math.max(60, Math.min(140, durationTicks));
        }
    }

    public static final class AreaBox {
        public int x1;
        public int y1;
        public int z1;
        public int x2;
        public int y2;
        public int z2;

        public AreaBox() {
        }

        public AreaBox(BlockPos from, BlockPos to) {
            this.x1 = from.getX();
            this.y1 = from.getY();
            this.z1 = from.getZ();
            this.x2 = to.getX();
            this.y2 = to.getY();
            this.z2 = to.getZ();
        }

        public boolean isValid() {
            return !(x1 == x2 && y1 == y2 && z1 == z2);
        }

        public BlockPos min() {
            return new BlockPos(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2));
        }

        public BlockPos max() {
            return new BlockPos(Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
        }

        public AABB toAabb() {
            BlockPos min = min();
            BlockPos max = max();
            return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0);
        }
    }
}
