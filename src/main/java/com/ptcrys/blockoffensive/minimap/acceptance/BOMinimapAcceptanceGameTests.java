package com.ptcrys.blockoffensive.minimap.acceptance;

import com.ptcrys.blockoffensive.minimap.CSGameMinimapRegionProvider;
import com.ptcrys.fpsmatch.common.capability.map.MinimapCapability;
import com.ptcrys.fpsmatch.common.minimap.server.DraftAncestorPins;
import com.ptcrys.fpsmatch.common.minimap.server.DraftStore;
import com.ptcrys.fpsmatch.common.minimap.server.MinimapBindingCoordinator;
import com.ptcrys.fpsmatch.common.minimap.server.MinimapPermissionPolicy;
import com.ptcrys.fpsmatch.common.minimap.server.sync.GameplayRegionRuntimeResolver;
import com.ptcrys.fpsmatch.common.minimap.server.sync.RuntimeMapSource;
import com.ptcrys.fpsmatch.core.minimap.format.CanonicalPngCodecV1;
import com.ptcrys.fpsmatch.core.minimap.format.MinimapContainerLayout;
import com.ptcrys.fpsmatch.core.minimap.format.RuntimeMap;
import com.ptcrys.fpsmatch.core.minimap.format.RuntimeMapReader;
import com.ptcrys.fpsmatch.core.minimap.format.SourceMap;
import com.ptcrys.fpsmatch.core.minimap.format.SourceMapReader;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeEntryDescriptor;
import com.ptcrys.fpsmatch.core.minimap.region.MinimapRegionProvider;
import com.ptcrys.fpsmatch.core.minimap.region.RuntimeRegionDescriptor;
import com.ptcrys.fpsmatch.core.minimap.region.RuntimeRegionMerger;
import com.ptcrys.fpsmatch.core.minimap.region.WorldAxisAlignedBounds;
import com.ptcrys.fpsmatch.core.minimap.storage.MinimapRepository;
import com.ptcrys.fpsmatch.core.minimap.storage.PublishTransaction;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder("blockoffensive")
@PrefixGameTestTemplate(false)
public final class BOMinimapAcceptanceGameTests {
    private static final NamespacedId DIMENSION = NamespacedId.parse("minecraft:overworld");
    private static final UUID ACTOR = new UUID(0L, 201L);
    private static final ContainerPath SOURCE_TILE = ContainerPath.parse(
            "floors/ground/layers/paint/tiles/0_0.png");
    private static final ContainerPath RUNTIME_TILE = ContainerPath.parse(
            "floors/ground/tiles/0/0_0.png");

    private BOMinimapAcceptanceGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void csPublicationUsesRealEditorAndCleansAuthority(GameTestHelper helper) {
        runRoundTrip(helper, "cs", 4);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void csdmPublicationOmitsBombProfileAndCleansAuthority(GameTestHelper helper) {
        runRoundTrip(helper, "csdm", 3);
    }

    private static void runRoundTrip(
            GameTestHelper helper,
            String gameType,
            int expectedProfiles
    ) {
        Path root = helper.getLevel().getServer().getWorldPath(LevelResource.ROOT)
                .resolve("fpsmatch").resolve("gametest-acceptance-seed")
                .resolve(gameType).toAbsolutePath().normalize();
        try {
            deleteOwnedRoot(root);
            executeRoundTrip(root, gameType, expectedProfiles);
            deleteOwnedRoot(root);
            helper.succeed();
        } catch (Throwable failure) {
            try {
                deleteOwnedRoot(root);
            } catch (Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            helper.fail("Acceptance " + gameType + " round-trip failed: " + failure);
        }
    }

    private static void executeRoundTrip(
            Path root,
            String gameType,
            int expectedProfiles
    ) throws IOException {
        MapKey mapKey = new MapKey(gameType, "Acceptance " + gameType);
        NamespacedId documentId = NamespacedId.parse(
                "blockoffensive:gametest_" + gameType);
        List<RuntimeRegionDescriptor> live = regions(gameType);
        MinimapRepository repository = new MinimapRepository(root.resolve("repository"));
        PublishTransaction abandoned = repository.reserve(
                mapKey, DIMENSION, documentId, 0L);
        repository.abort(abandoned, "consume high-water");
        MemoryBindings bindingStore = new MemoryBindings();
        Path draftRoot = root.resolve("fixture").resolve("drafts");
        BOMinimapAcceptancePublication.DraftStoreFactory stores =
                new BOMinimapAcceptancePublication.DraftStoreFactory() {
                    @Override
                    public Path root() {
                        return draftRoot;
                    }

                    @Override
                    public DraftStore open() {
                        return new DraftStore(
                                draftRoot, Duration.ofHours(1), 16, Clock.systemUTC(),
                                DraftAncestorPins.repository(repository));
                    }
                };
        MinimapPermissionPolicy permissions =
                (actor, map, action) -> Optional.of(ACTOR.equals(actor));
        byte[] visibleTile = visibleTile();
        BOMinimapAcceptancePublication publication =
                new BOMinimapAcceptancePublication(
                        new BOMinimapAcceptancePublication.Scope(
                                ACTOR, mapKey, DIMENSION, documentId),
                        repository, stores, permissions, bindingStore, Clock.systemUTC(),
                        (actor, message) -> { }, ignored -> { });

        BOMinimapAcceptancePublication.Snapshot snapshot = publication.initialize(
                live, visibleTile,
                UUID.nameUUIDFromBytes(
                        (gameType + ":publish").getBytes(StandardCharsets.UTF_8)));
        require(snapshot.seedRevision() == 2L,
                "seed did not use the repository high-water mark");
        require(snapshot.publishedRevision() == 3L,
                "final publish did not use the next high-water revision");
        require(pinCount(repository, mapKey) == 1L,
                "active editor draft did not pin its seed ancestor");
        verifyRevision(repository, mapKey, 2L, live, expectedProfiles, null);
        verifyRevision(
                repository, mapKey, snapshot.publishedRevision(),
                live, expectedProfiles, visibleTile);

        publication.close();
        require(bindingStore.read(mapKey).isEmpty(),
                "fixture binding survived publication close");
        require(repository.currentPublication(mapKey).isEmpty(),
                "fixture CURRENT survived publication close");
        require(pinCount(repository, mapKey) == 0L,
                "publication close leaked the draft ancestor pin");
        require(directoryEmpty(draftRoot),
                "publication close left a draft record");
    }

    private static void verifyRevision(
            MinimapRepository repository,
            MapKey mapKey,
            long revision,
            List<RuntimeRegionDescriptor> live,
            int expectedProfiles,
            byte[] expectedVisibleTile
    ) throws IOException {
        Path revisionRoot = repository.mapDirectory(mapKey).resolve("revisions")
                .resolve(Long.toString(revision));
        try (SourceMap source = SourceMapReader.read(
                Files.readAllBytes(revisionRoot.resolve("source.fpsmap")));
             RuntimeMap runtime = RuntimeMapReader.read(
                     Files.readAllBytes(revisionRoot.resolve("runtime.fpsmapc")))) {
            require(source.definition().regions().regions().size() == live.size(),
                    "source region count is incorrect");
            require(source.definition().styles().styles().size() == expectedProfiles,
                    "source style count is incorrect");
            require(runtime.paths().stream()
                            .filter(path -> MinimapContainerLayout
                                    .parseRuntimeTile(path).isPresent())
                            .count() == 16L,
                    "runtime tile coverage is incomplete");
            require(RuntimeRegionMerger.merge(
                    runtime.manifest(), runtime.definition().regions(), live
            ).equals(runtime.definition().regions()),
                    "live region merge changed the committed runtime regions");
            verifyResolverKeepsCommittedIdentity(runtime, mapKey, live);
            if (expectedVisibleTile != null) {
                require(Arrays.equals(source.entryBytes(SOURCE_TILE), expectedVisibleTile),
                        "final source did not retain the editor tile");
                require(Arrays.equals(runtime.entryBytes(RUNTIME_TILE), expectedVisibleTile),
                        "final runtime did not compile the editor tile");
                byte[] transparent = CanonicalPngCodecV1.encode(
                        128, 128, new byte[128 * 128 * 4]);
                require(!Arrays.equals(runtime.entryBytes(RUNTIME_TILE), transparent),
                        "final runtime still contains the transparent seed tile");
            }
        }
    }

    private static void verifyResolverKeepsCommittedIdentity(
            RuntimeMap runtime,
            MapKey mapKey,
            List<RuntimeRegionDescriptor> live
    ) {
        WireIdentity.MapTarget target = new WireIdentity.MapTarget(mapKey, DIMENSION);
        RuntimeMapSource source = runtimeSource(runtime, target);
        MinimapRegionProvider provider = (requestedMap, defaultFloor) -> live;
        GameplayRegionRuntimeResolver resolver = new GameplayRegionRuntimeResolver(
                (actor, requestedTarget) -> Optional.of(source),
                requestedMap -> List.of(provider));
        RuntimeMapSource resolved = resolver.resolve(ACTOR, target).orElseThrow();
        require(resolved == source,
                "live region merge derived a replacement runtime source");
        require(resolved.identity().equals(source.identity()),
                "live region merge changed the committed runtime identity");
        require(resolved.identity().runtimeHash().equals(runtime.runtimeHash()),
                "live region merge changed the committed runtime hash");
    }

    private static RuntimeMapSource runtimeSource(
            RuntimeMap runtime,
            WireIdentity.MapTarget target
    ) {
        WireIdentity.RuntimeIdentity identity = new WireIdentity.RuntimeIdentity(
                new WireIdentity.DocumentBinding(target, runtime.manifest().documentId()),
                runtime.manifest().publishRevision(), runtime.runtimeHash(),
                Optional.of(runtime.runtimeContainerHash()));
        return new RuntimeMapSource() {
            @Override
            public WireIdentity.RuntimeIdentity identity() {
                return identity;
            }

            @Override
            public byte[] manifestBytes() {
                return runtime.manifestBytes();
            }

            @Override
            public Optional<RuntimeEntryDescriptor> descriptor(ContainerPath path) {
                return runtime.manifest().entries().stream()
                        .filter(entry -> entry.path().equals(path))
                        .findFirst();
            }

            @Override
            public InputStream openEntry(ContainerPath path) {
                return runtime.openEntry(path);
            }

            @Override
            public void close() {
            }
        };
    }

    private static byte[] visibleTile() {
        byte[] rgba = new byte[128 * 128 * 4];
        for (int offset = 0; offset < rgba.length; offset += 4) {
            rgba[offset] = 30;
            rgba[offset + 1] = 50;
            rgba[offset + 2] = 70;
            rgba[offset + 3] = (byte) 255;
        }
        return CanonicalPngCodecV1.encode(128, 128, rgba);
    }

    private static List<RuntimeRegionDescriptor> regions(String gameType) {
        RuntimeRegionDescriptor boundary = region(
                "map_boundary", CSGameMinimapRegionProvider.SEMANTIC_MAP_BOUNDARY,
                "fpsmatch:map/boundary", -64, -64, 64, 64, 10);
        RuntimeRegionDescriptor spawnCt = region(
                "spawn_ct", CSGameMinimapRegionProvider.SEMANTIC_SPAWN,
                "fpsmatch:spawn/team/ct", -50, -50, -42, -42, 50);
        RuntimeRegionDescriptor spawnT = region(
                "spawn_t", CSGameMinimapRegionProvider.SEMANTIC_SPAWN,
                "fpsmatch:spawn/team/t", 42, 42, 50, 50, 50);
        RuntimeRegionDescriptor shopCt = region(
                "shop_ct", CSGameMinimapRegionProvider.SEMANTIC_SHOP,
                "fpsmatch:shop/team/ct", -46, -40, -38, -32, 40);
        RuntimeRegionDescriptor shopT = region(
                "shop_t", CSGameMinimapRegionProvider.SEMANTIC_SHOP,
                "fpsmatch:shop/team/t", 34, 30, 42, 38, 40);
        if (gameType.equals("csdm")) {
            return List.of(boundary, spawnCt, spawnT, shopCt, shopT);
        }
        return List.of(
                boundary,
                region("site_a", CSGameMinimapRegionProvider.SEMANTIC_BOMB_SITE,
                        "fpsmatch:gameplay/site_a", -20, -15, -8, -3, 100),
                region("site_b", CSGameMinimapRegionProvider.SEMANTIC_BOMB_SITE,
                        "fpsmatch:gameplay/site_b", 12, 18, 26, 30, 100),
                spawnCt,
                spawnT,
                shopCt,
                shopT);
    }

    private static RuntimeRegionDescriptor region(
            String id,
            String semantic,
            String reference,
            double minX,
            double minZ,
            double maxX,
            double maxZ,
            int priority
    ) {
        return new RuntimeRegionDescriptor(
                id, "ground", id, semantic, List.of(), Optional.of(reference),
                new WorldAxisAlignedBounds(minX, 0, minZ, maxX, 8, maxZ), priority);
    }

    private static long pinCount(MinimapRepository repository, MapKey mapKey)
            throws IOException {
        Path pins = repository.mapDirectory(mapKey).resolve("pins");
        if (!Files.isDirectory(pins)) {
            return 0L;
        }
        try (var entries = Files.list(pins)) {
            return entries.count();
        }
    }

    private static boolean directoryEmpty(Path directory) throws IOException {
        try (var entries = Files.list(directory)) {
            return entries.findAny().isEmpty();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static void deleteOwnedRoot(Path root) throws IOException {
        Path normalized = root.toAbsolutePath().normalize();
        if (!normalized.toString().contains("gametest-acceptance-seed")) {
            throw new IllegalStateException("Unexpected acceptance GameTest root");
        }
        if (!Files.exists(normalized)) {
            return;
        }
        try (var paths = Files.walk(normalized)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static final class MemoryBindings
            implements MinimapBindingCoordinator.BindingStore {
        private Optional<MinimapCapability.Binding> value = Optional.empty();

        @Override
        public Optional<MinimapCapability.Binding> read(MapKey mapKey) {
            return value;
        }

        @Override
        public void write(MapKey mapKey, MinimapCapability.Binding binding) {
            value = Optional.of(binding);
        }

        @Override
        public void clear(MapKey mapKey) {
            value = Optional.empty();
        }

        @Override
        public MinimapCapability.BindingClearResult compareAndClear(
                MapKey mapKey,
                MinimapCapability.Binding expected
        ) {
            if (value.isEmpty()) {
                return MinimapCapability.BindingClearResult.ALREADY_ABSENT;
            }
            if (!value.orElseThrow().equals(expected)) {
                return MinimapCapability.BindingClearResult.MISMATCH;
            }
            value = Optional.empty();
            return MinimapCapability.BindingClearResult.CLEARED;
        }
    }
}
