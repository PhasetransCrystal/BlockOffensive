package com.ptcrys.blockoffensive.minimap.acceptance;

import com.ptcrys.blockoffensive.minimap.CSGameMinimapRegionProvider;
import com.ptcrys.fpsmatch.common.capability.map.MinimapCapability;
import com.ptcrys.fpsmatch.common.minimap.server.DraftAncestorPins;
import com.ptcrys.fpsmatch.common.minimap.server.DraftState;
import com.ptcrys.fpsmatch.common.minimap.server.DraftStore;
import com.ptcrys.fpsmatch.common.minimap.server.MinimapBindingCoordinator;
import com.ptcrys.fpsmatch.common.minimap.server.MinimapPermissionPolicy;
import com.ptcrys.fpsmatch.core.minimap.format.CanonicalPngCodecV1;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.region.RuntimeRegionDescriptor;
import com.ptcrys.fpsmatch.core.minimap.region.WorldAxisAlignedBounds;
import com.ptcrys.fpsmatch.core.minimap.storage.CurrentPublication;
import com.ptcrys.fpsmatch.core.minimap.storage.MinimapRepository;
import com.ptcrys.fpsmatch.core.minimap.storage.PublishOutcome;
import com.ptcrys.fpsmatch.core.minimap.storage.PublishTransaction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder("blockoffensive")
@PrefixGameTestTemplate(false)
public final class BOMinimapAcceptancePublicationGameTests {
    private static final UUID ACTOR = new UUID(0L, 301L);
    private static final NamespacedId DIMENSION = NamespacedId.parse("minecraft:overworld");

    private BOMinimapAcceptancePublicationGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void activeDraftWithoutReturnedIdIsRecovered(GameTestHelper helper) {
        run(helper, "active-without-id", scenario -> {
            scenario.stores.failAfterCreate = true;
            AcceptanceFixtureRegistry<PublicationHandle> registry = new AcceptanceFixtureRegistry<>();

            requireThrows(() -> registry.create(ACTOR, scenario::handle));
            require(registry.find(ACTOR).isPresent(), "failed initialize lost registry handle");
            require(pinCount(scenario.repository, scenario.mapKey) == 1L,
                    "active after-effect did not retain its ancestor pin");
            require(Files.exists(scenario.fixtureRoot),
                    "active after-effect lost its private recovery root");

            require(registry.cleanup(ACTOR), "active after-effect cleanup was not attempted");
            require(pinCount(scenario.repository, scenario.mapKey) == 0L,
                    "active after-effect pin was not released");
            require(!Files.exists(scenario.fixtureRoot),
                    "private root was not removed after draft release");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void discardFailureKeepsRootAndRetries(GameTestHelper helper) {
        run(helper, "discard-retry", scenario -> {
            AcceptanceFixtureRegistry<PublicationHandle> registry = new AcceptanceFixtureRegistry<>();
            registry.create(ACTOR, scenario::handle);
            scenario.pins.failUnpinCount = 1;

            requireThrows(() -> registry.cleanup(ACTOR));
            require(registry.find(ACTOR).isPresent(), "failed discard lost registry handle");
            require(Files.exists(scenario.fixtureRoot), "failed discard deleted recovery root");
            require(pinCount(scenario.repository, scenario.mapKey) == 1L,
                    "failed discard unexpectedly released the pin");

            require(registry.cleanup(ACTOR), "discard retry did not complete");
            require(pinCount(scenario.repository, scenario.mapKey) == 0L,
                    "discard retry did not release the pin");
            require(!Files.exists(scenario.fixtureRoot),
                    "discard retry did not remove the private root");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void seedPendingRecoveryCannotReviveAfterClose(GameTestHelper helper) {
        run(helper, "seed-pending", scenario -> {
            scenario.bindings.failWriteNumber = 1;
            AcceptanceFixtureRegistry<PublicationHandle> registry = new AcceptanceFixtureRegistry<>();

            requireThrows(() -> registry.create(ACTOR, scenario::handle));
            require(registry.find(ACTOR).isPresent(), "seed bind failure lost registry handle");
            require(scenario.bindings.read(scenario.mapKey).isEmpty(),
                    "seed bind failure did not restore the absent binding");

            require(registry.cleanup(ACTOR), "seed pending recovery cleanup did not complete");
            require(scenario.bindings.read(scenario.mapKey).isEmpty(),
                    "seed binding survived exact cleanup");
            require(scenario.repository.currentPublication(scenario.mapKey).isEmpty(),
                    "seed CURRENT survived exact cleanup");
            int writesAfterClose = scenario.bindings.writeCount;
            requireThrows(() -> scenario.publication.publish(ACTOR, UUID.randomUUID(), null));
            require(scenario.bindings.writeCount == writesAfterClose,
                    "terminal publication replayed a pending seed binding");
            require(scenario.bindings.read(scenario.mapKey).isEmpty(),
                    "terminal publication revived the seed binding");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void finalPendingRecoveryClearsSeedAndFinal(GameTestHelper helper) {
        run(helper, "final-pending", scenario -> {
            scenario.bindings.failWriteNumber = 2;
            AcceptanceFixtureRegistry<PublicationHandle> registry = new AcceptanceFixtureRegistry<>();

            requireThrows(() -> registry.create(ACTOR, scenario::handle));
            require(registry.find(ACTOR).isPresent(), "final bind failure lost registry handle");
            MinimapCapability.Binding restoredSeed = scenario.bindings.read(scenario.mapKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "final bind failure did not restore the seed binding"));
            require(restoredSeed.revision()
                            < scenario.repository.currentPublication(scenario.mapKey)
                            .orElseThrow().pointer().revision(),
                    "final bind failure did not leave seed binding under final CURRENT");

            require(registry.cleanup(ACTOR), "final pending recovery cleanup did not complete");
            require(scenario.bindings.read(scenario.mapKey).isEmpty(),
                    "seed or final binding survived cleanup");
            require(scenario.repository.currentPublication(scenario.mapKey).isEmpty(),
                    "final CURRENT survived cleanup");
            int writesAfterClose = scenario.bindings.writeCount;
            requireThrows(() -> scenario.publication.publish(ACTOR, UUID.randomUUID(), null));
            require(scenario.bindings.writeCount == writesAfterClose,
                    "terminal publication replayed a pending final binding");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void bindingUnavailableBlocksAllLaterCleanup(GameTestHelper helper) {
        run(helper, "binding-unavailable", scenario -> {
            AcceptanceFixtureRegistry<PublicationHandle> registry = new AcceptanceFixtureRegistry<>();
            registry.create(ACTOR, scenario::handle);
            CurrentPublication before = scenario.repository.currentPublication(scenario.mapKey)
                    .orElseThrow();
            scenario.bindings.unavailableClearCount = 1;

            requireThrows(() -> registry.cleanup(ACTOR));
            require(registry.find(ACTOR).isPresent(), "unavailable binding lost registry handle");
            require(scenario.repository.currentPublication(scenario.mapKey)
                            .map(before::equals).orElse(false),
                    "binding UNAVAILABLE advanced CURRENT cleanup");
            require(pinCount(scenario.repository, scenario.mapKey) == 1L,
                    "binding UNAVAILABLE advanced draft cleanup");
            require(Files.exists(scenario.fixtureRoot),
                    "binding UNAVAILABLE advanced root cleanup");

            require(registry.cleanup(ACTOR), "binding cleanup retry did not complete");
            require(pinCount(scenario.repository, scenario.mapKey) == 0L,
                    "binding cleanup retry leaked the pin");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void foreignAuthoritySurvivesCanonicalRecoveryAndClose(GameTestHelper helper) {
        run(helper, "foreign-authority", scenario -> {
            AcceptanceFixtureRegistry<PublicationHandle> registry = new AcceptanceFixtureRegistry<>();
            registry.create(ACTOR, scenario::handle);
            long fixtureRevision = scenario.publication.snapshot().publishedRevision();
            PublishTransaction foreignTransaction = scenario.repository.reserve(
                    scenario.mapKey, DIMENSION, scenario.documentId, fixtureRevision);
            BOMinimapAcceptanceSourceSeed.CompiledSeed compiled =
                    BOMinimapAcceptanceSourceSeed.compile(
                            scenario.mapKey, DIMENSION, scenario.documentId,
                            foreignTransaction.publishRevision(), scenario.regions);
            PublishOutcome outcome = scenario.repository.commit(scenario.repository.prepare(
                    foreignTransaction, compiled.sourceBytes(), compiled.runtimeBytes()));
            require(outcome.committed(), "foreign publication was not committed");
            CurrentPublication foreignCurrent = scenario.repository
                    .currentPublication(scenario.mapKey).orElseThrow();
            MinimapCapability.Binding foreignBinding = bindingFor(foreignCurrent);
            scenario.bindings.write(scenario.mapKey, foreignBinding);
            Files.writeString(
                    scenario.repository.mapDirectory(scenario.mapKey)
                            .resolve("RECOVERY_REQUIRED"),
                    "{}", StandardCharsets.UTF_8);

            require(registry.cleanup(ACTOR), "foreign-state cleanup did not complete");
            require(scenario.bindings.read(scenario.mapKey)
                            .filter(foreignBinding::equals).isPresent(),
                    "exact binding cleanup removed foreign authority");
            require(scenario.repository.currentPublication(scenario.mapKey)
                            .filter(foreignCurrent::equals).isPresent(),
                    "exact CURRENT cleanup removed foreign authority");
            require(pinCount(scenario.repository, scenario.mapKey) == 0L,
                    "foreign authority cleanup leaked the fixture draft pin");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void unknownDraftRootEntryKeepsRecoveryState(GameTestHelper helper) {
        run(helper, "unknown-root-entry", scenario -> {
            AcceptanceFixtureRegistry<PublicationHandle> registry = new AcceptanceFixtureRegistry<>();
            registry.create(ACTOR, scenario::handle);
            Path unknown = scenario.stores.root().resolve("unknown-entry");
            Files.writeString(unknown, "unexpected", StandardCharsets.UTF_8);

            requireThrows(() -> registry.cleanup(ACTOR));
            require(registry.find(ACTOR).isPresent(), "root scan failure lost registry handle");
            require(Files.exists(scenario.fixtureRoot), "root scan failure deleted recovery state");
            require(pinCount(scenario.repository, scenario.mapKey) == 1L,
                    "root scan failure discarded the active draft");

            Files.delete(unknown);
            require(registry.cleanup(ACTOR), "root scan retry did not complete");
            require(pinCount(scenario.repository, scenario.mapKey) == 0L,
                    "root scan retry leaked the pin");
        });
    }

    private static void run(
            GameTestHelper helper,
            String slug,
            ThrowingConsumer<Scenario> test
    ) {
        Path root = helper.getLevel().getServer().getWorldPath(LevelResource.ROOT)
                .resolve("fpsmatch").resolve("gametest-acceptance-publication")
                .resolve(slug).toAbsolutePath().normalize();
        try {
            deleteScenarioRoot(root);
            test.accept(new Scenario(root, slug));
            deleteScenarioRoot(root);
            helper.succeed();
        } catch (Throwable failure) {
            try {
                deleteScenarioRoot(root);
            } catch (Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            helper.fail("Acceptance publication " + slug + " failed: " + failure);
        }
    }

    private static MinimapCapability.Binding bindingFor(CurrentPublication publication) {
        var descriptor = publication.record().descriptor();
        return new MinimapCapability.Binding(
                publication.target().dimension(), publication.target().documentId(),
                descriptor.publishRevision(), descriptor.sourceHash(), descriptor.runtimeHash());
    }

    private static byte[] visibleTile() {
        byte[] rgba = new byte[128 * 128 * 4];
        for (int offset = 0; offset < rgba.length; offset += 4) {
            rgba[offset] = 35;
            rgba[offset + 1] = 65;
            rgba[offset + 2] = 85;
            rgba[offset + 3] = (byte) 255;
        }
        return CanonicalPngCodecV1.encode(128, 128, rgba);
    }

    private static List<RuntimeRegionDescriptor> regions(String gameType) {
        List<RuntimeRegionDescriptor> common = List.of(
                region("map_boundary", CSGameMinimapRegionProvider.SEMANTIC_MAP_BOUNDARY,
                        "fpsmatch:map/boundary",
                        -64, -64, 64, 64, 10),
                region("spawn_ct", CSGameMinimapRegionProvider.SEMANTIC_SPAWN,
                        "fpsmatch:spawn/team/ct",
                        -50, -50, -42, -42, 50),
                region("shop_ct", CSGameMinimapRegionProvider.SEMANTIC_SHOP,
                        "fpsmatch:shop/team/ct",
                        -46, -40, -38, -32, 40));
        if (gameType.equals("csdm")) {
            return common;
        }
        return List.of(
                common.get(0),
                region("site_a", CSGameMinimapRegionProvider.SEMANTIC_BOMB_SITE,
                        "fpsmatch:gameplay/site_a",
                        -20, -15, -8, -3, 100),
                common.get(1), common.get(2));
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
        if (!Files.isDirectory(pins, LinkOption.NOFOLLOW_LINKS)) {
            return 0L;
        }
        try (var entries = Files.list(pins)) {
            return entries.count();
        }
    }

    private static void deleteScenarioRoot(Path root) throws IOException {
        Path normalized = root.toAbsolutePath().normalize();
        if (!normalized.toString().contains("gametest-acceptance-publication")) {
            throw new IllegalStateException("Unexpected acceptance publication root");
        }
        if (!Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.walk(normalized)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void requireThrows(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new IllegalStateException("Expected operation to fail");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }

    private static final class Scenario {
        private final Path root;
        private final Path fixtureRoot;
        private final MapKey mapKey;
        private final NamespacedId documentId;
        private final List<RuntimeRegionDescriptor> regions;
        private final MinimapRepository repository;
        private final FaultingPins pins;
        private final StoreFactory stores;
        private final MemoryBindings bindings;
        private final BOMinimapAcceptancePublication publication;

        private Scenario(Path root, String slug) {
            this.root = root;
            this.fixtureRoot = root.resolve("fixture").toAbsolutePath().normalize();
            this.mapKey = new MapKey("cs", "Acceptance " + slug);
            this.documentId = NamespacedId.parse(
                    "blockoffensive:" + slug.replace('-', '_'));
            this.regions = regions(mapKey.gameType());
            this.repository = new MinimapRepository(root.resolve("repository"));
            this.pins = new FaultingPins(DraftAncestorPins.repository(repository));
            this.stores = new StoreFactory(fixtureRoot.resolve("drafts"), pins);
            this.bindings = new MemoryBindings();
            MinimapPermissionPolicy permissions =
                    (actor, map, action) -> Optional.of(ACTOR.equals(actor));
            this.publication = new BOMinimapAcceptancePublication(
                    new BOMinimapAcceptancePublication.Scope(
                            ACTOR, mapKey, DIMENSION, documentId),
                    repository, stores, permissions, bindings, Clock.systemUTC(),
                    (actor, message) -> { }, ignored -> { });
        }

        private PublicationHandle handle() {
            return new PublicationHandle(this);
        }
    }

    private static final class PublicationHandle implements AcceptanceFixtureRegistry.Handle {
        private final Scenario scenario;

        private PublicationHandle(Scenario scenario) {
            this.scenario = scenario;
        }

        @Override
        public void initialize() {
            scenario.publication.initialize(
                    scenario.regions, visibleTile(),
                    UUID.nameUUIDFromBytes(
                            scenario.documentId.toString().getBytes(StandardCharsets.UTF_8)));
        }

        @Override
        public void close() {
            scenario.publication.close();
            try {
                deleteScenarioRoot(scenario.fixtureRoot);
            } catch (IOException failure) {
                throw new IllegalStateException("Unable to delete fixture root", failure);
            }
        }
    }

    private static final class StoreFactory
            implements BOMinimapAcceptancePublication.DraftStoreFactory {
        private final Path root;
        private final DraftAncestorPins pins;
        private boolean failAfterCreate;
        private boolean failNextOpen;

        private StoreFactory(Path root, DraftAncestorPins pins) {
            this.root = root.toAbsolutePath().normalize();
            this.pins = pins;
        }

        @Override
        public Path root() {
            return root;
        }

        @Override
        public DraftStore open() {
            if (failNextOpen) {
                failNextOpen = false;
                throw new IllegalStateException("injected draft store open failure");
            }
            return new DraftStore(
                    root, Duration.ofHours(1), 16, Clock.systemUTC(), pins);
        }

        @Override
        public void afterCreate(DraftState created) {
            if (failAfterCreate) {
                failAfterCreate = false;
                throw new IllegalStateException("injected active draft return failure");
            }
        }
    }

    private static final class FaultingPins implements DraftAncestorPins {
        private final DraftAncestorPins delegate;
        private int failUnpinCount;

        private FaultingPins(DraftAncestorPins delegate) {
            this.delegate = delegate;
        }

        @Override
        public void pin(
                MapKey mapKey,
                long revision,
                Sha256 expectedSourceHash,
                String pinId
        ) {
            delegate.pin(mapKey, revision, expectedSourceHash, pinId);
        }

        @Override
        public void unpin(MapKey mapKey, long revision, String pinId) {
            if (failUnpinCount > 0) {
                failUnpinCount--;
                throw new IllegalStateException("injected ancestor unpin failure");
            }
            delegate.unpin(mapKey, revision, pinId);
        }
    }

    private static final class MemoryBindings
            implements MinimapBindingCoordinator.BindingStore {
        private Optional<MinimapCapability.Binding> value = Optional.empty();
        private int writeCount;
        private int failWriteNumber;
        private int unavailableClearCount;

        @Override
        public Optional<MinimapCapability.Binding> read(MapKey mapKey) {
            return value;
        }

        @Override
        public void write(MapKey mapKey, MinimapCapability.Binding binding) {
            writeCount++;
            if (writeCount == failWriteNumber) {
                throw new IllegalStateException("injected binding write failure");
            }
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
            if (unavailableClearCount > 0) {
                unavailableClearCount--;
                return MinimapCapability.BindingClearResult.UNAVAILABLE;
            }
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
