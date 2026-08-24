package com.ptcrys.blockoffensive.minimap.acceptance;

import com.ptcrys.blockoffensive.BlockOffensive;
import com.ptcrys.blockoffensive.minimap.BlockOffensiveMinimapRuntime;
import com.ptcrys.blockoffensive.minimap.CSGameMinimapRegionSources;
import com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceAck;
import com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceEditorContext;
import com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceEpochGate;
import com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceSceneS2CPacket;
import com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceSceneSignal;
import com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceServerLedger;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.capability.map.MinimapCapability;
import com.ptcrys.fpsmatch.common.mapselect.MapRoomQueryService;
import com.ptcrys.fpsmatch.common.minimap.server.DefaultMinimapPermissionPolicy;
import com.ptcrys.fpsmatch.common.minimap.server.DraftAck;
import com.ptcrys.fpsmatch.common.minimap.server.DraftAncestorPins;
import com.ptcrys.fpsmatch.common.minimap.server.DraftException;
import com.ptcrys.fpsmatch.common.minimap.server.DraftStore;
import com.ptcrys.fpsmatch.common.minimap.server.EditorSession;
import com.ptcrys.fpsmatch.common.minimap.server.MinimapAction;
import com.ptcrys.fpsmatch.common.minimap.server.SessionAccessException;
import com.ptcrys.fpsmatch.common.packet.minimap.ForgeMinimapServerRuntimeRegistration;
import com.ptcrys.fpsmatch.core.FPSMCore;
import com.ptcrys.fpsmatch.core.map.BaseMap;
import com.ptcrys.fpsmatch.core.minimap.format.CanonicalPngCodecV1;
import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import com.ptcrys.fpsmatch.core.minimap.marker.MarkerCandidate;
import com.ptcrys.fpsmatch.core.minimap.marker.MarkerSnapshot;
import com.ptcrys.fpsmatch.core.minimap.marker.MinimapViewerContext;
import com.ptcrys.fpsmatch.core.minimap.marker.MinimapVisibilityPolicy;
import com.ptcrys.fpsmatch.core.minimap.model.CanvasBounds;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.region.MinimapRegionProvider;
import com.ptcrys.fpsmatch.core.minimap.region.RuntimeRegionDescriptor;
import com.ptcrys.fpsmatch.core.minimap.storage.CurrentPublication;
import com.ptcrys.fpsmatch.core.minimap.storage.CurrentResetResult;
import com.ptcrys.fpsmatch.core.minimap.storage.MinimapRepository;
import com.ptcrys.fpsmatch.core.minimap.wire.PublishWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;
import com.ptcrys.fpsmatch.core.minimap.wire.WireStatus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Server-authoritative, non-production minimap acceptance fixture.
 *
 * The fixture uses the editor draft/materialization/compiler path and the live
 * BO runtime providers. It never sends a success result for an unavailable,
 * denied, or stale operation, and cleanup restores authority before removing
 * all fixture-owned persistence.
 */
public final class BOMinimapAcceptanceFixture implements AcceptanceFixtureRegistry.Handle {
    private static final long ACK_TIMEOUT_TICKS = 100L;

    public enum Scene {
        HUD_CROWDED("hud_crowded"),
        TACTICAL("tactical"),
        EDITOR_DIRTY("editor_dirty"),
        EDITOR_PUBLISHING("editor_publishing"),
        EDITOR_ERROR("editor_error"),
        MAP_ROOM_ERROR("map_room_error");

        private final String commandName;

        Scene(String commandName) {
            this.commandName = commandName;
        }

        public String commandName() {
            return commandName;
        }

        public static Scene parse(String value) {
            for (Scene scene : values()) {
                if (scene.commandName.equals(value)) {
                    return scene;
                }
            }
            throw new IllegalArgumentException("Unknown minimap acceptance scene: " + value);
        }
    }

    private final MinecraftServer server;
    private final ServerPlayer actor;
    private final BaseMap map;
    private final MapKey mapKey;
    private final NamespacedId dimension;
    private final NamespacedId documentId;
    private final UUID runId;
    private final Object actorConnection;
    private final Path canonicalRoot;
    private final Path acceptanceRoot;
    private final Path fixtureRoot;
    private final MinimapRepository repository;
    private final com.ptcrys.fpsmatch.common.minimap.server.MinimapBindingCoordinator.BindingStore bindingStore;
    private final BOMinimapAcceptancePublication publication;
    private final BOMinimapAcceptanceServerLedger acceptanceLedger;
    private final BOMinimapAcceptanceCompositeSender sceneSender;
    private final String documentSlug;
    private UUID draftId;
    private EditorSession session;
    private Sha256 draftRoot;
    private long seedRevision;
    private Sha256 seedSourceHash;
    private MinimapCapability.Binding publishedBinding;
    private Scene scene;
    private long sceneRevision;
    private long committedSceneEpoch;
    private BOMinimapAcceptanceSceneSignal cleanupSignal;
    private boolean initialized;
    private boolean cleanupStarted;
    private boolean privateCleanupComplete;
    private boolean closed;

    private BOMinimapAcceptanceFixture(
            MinecraftServer server,
            ServerPlayer actor,
            BaseMap map,
            String requestedGameType
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.actor = Objects.requireNonNull(actor, "actor");
        this.map = Objects.requireNonNull(map, "map");
        if (!requestedGameType.equals(map.getGameType())) {
            throw new IllegalStateException(
                    "Acceptance fixture requires active " + requestedGameType
                            + " map; found " + map.getGameType()
            );
        }
        if (!BlockOffensiveMinimapRuntime.supports(
                new MapKey(map.getGameType(), map.getMapName()))) {
            throw new IllegalStateException("Active map is not a CS/CSDM minimap map");
        }
        this.mapKey = new MapKey(map.getGameType(), map.getMapName());
        this.dimension = NamespacedId.parse(
                map.getServerLevel().dimension().location().toString()
        );
        this.runId = UUID.randomUUID();
        this.actorConnection = Objects.requireNonNull(
                actor.connection, "acceptance actor connection"
        );
        this.acceptanceLedger = new BOMinimapAcceptanceServerLedger(
                actor.getUUID(), runId, this::matchesAuthoritativeEditorContext
        );
        this.sceneSender = new BOMinimapAcceptanceCompositeSender(
                actor, map, acceptanceLedger
        );
        this.documentSlug = "acceptance_" + map.getGameType() + "_"
                + runId.toString().replace("-", "");
        this.documentId = NamespacedId.parse("blockoffensive:" + documentSlug);
        this.canonicalRoot = server.getWorldPath(LevelResource.ROOT)
                .resolve("fpsmatch").resolve("minimaps")
                .toAbsolutePath().normalize();
        this.acceptanceRoot = canonicalRoot.resolve("acceptance")
                .resolve(actor.getUUID().toString())
                .toAbsolutePath().normalize();
        this.fixtureRoot = acceptanceRoot.resolve(runId.toString()).normalize();
        this.repository = new MinimapRepository(this.canonicalRoot);
        this.bindingStore = new com.ptcrys.fpsmatch.common.minimap.server.ServerEditorPublishService.CapabilityBindingStore();
        clearStaleAcceptanceState(repository, bindingStore, mapKey);
        if (bindingStore.read(mapKey).isPresent()) {
            throw new IllegalStateException(
                    "Acceptance fixture refuses to replace an existing minimap binding"
            );
        }
        if (repository.currentPublication(mapKey).isPresent()) {
            throw new IllegalStateException(
                    "Acceptance fixture refuses to replace an existing minimap publication"
            );
        }
        DefaultMinimapPermissionPolicy permission =
                new DefaultMinimapPermissionPolicy(
                        () -> 2,
                        this::permissionLevel
                );
        Path draftStoreRoot = fixtureRoot.resolve("drafts").normalize();
        BOMinimapAcceptancePublication.DraftStoreFactory draftStores =
                new BOMinimapAcceptancePublication.DraftStoreFactory() {
                    @Override
                    public Path root() {
                        return draftStoreRoot;
                    }

                    @Override
                    public DraftStore open() {
                        return new DraftStore(
                                draftStoreRoot,
                                Duration.ofHours(1),
                                16,
                                Clock.systemUTC(),
                                DraftAncestorPins.repository(repository));
                    }
                };
        this.publication = new BOMinimapAcceptancePublication(
                new BOMinimapAcceptancePublication.Scope(
                        actor.getUUID(), mapKey, dimension, documentId),
                repository, draftStores, permission, bindingStore, Clock.systemUTC(),
                (actorId, message) -> { },
                publishedMap -> {
                    // Match production publish ordering: revoke old runtime leases
                    // before the existing game-info refresh requests a new identity.
                    ForgeMinimapServerRuntimeRegistration.invalidateMap(server, publishedMap);
                    BlockOffensiveMinimapRuntime.reset(map);
                    map.pullGameInfo(actor);
        });
    }

    /**
     * Reclaims only the non-production acceptance authority left by a prior JVM.
     * Ordinary map publications remain authoritative and fail closed.
     */
    static void clearStaleAcceptanceState(
            MinimapRepository repository,
            com.ptcrys.fpsmatch.common.minimap.server.MinimapBindingCoordinator.BindingStore bindingStore,
            MapKey mapKey
    ) {
        Optional<MinimapCapability.Binding> binding = bindingStore.read(mapKey);
        Optional<CurrentPublication> current = repository.currentPublication(mapKey);
        boolean acceptanceBinding = binding
                .map(value -> isAcceptanceDocument(value.documentId(), mapKey))
                .orElse(false);
        boolean acceptanceCurrent = current
                .map(value -> isAcceptanceDocument(value.target().documentId(), mapKey))
                .orElse(false);
        if ((binding.isPresent() && !acceptanceBinding)
                || (current.isPresent() && !acceptanceCurrent)
                || (binding.isPresent() && current.isPresent()
                && acceptanceBinding != acceptanceCurrent)) {
            throw new IllegalStateException(
                    "Acceptance fixture refuses to replace ordinary minimap authority");
        }
        if (repository.isStrictJournalActive(mapKey)) {
            throw new IllegalStateException(
                    "Acceptance stale cleanup is unavailable for strict minimap storage");
        }
        if (binding.isPresent() && current.isPresent()) {
            CurrentPublication publication = current.orElseThrow();
            MinimapCapability.Binding visible = binding.orElseThrow();
            var descriptor = publication.record().descriptor();
            if (!publication.target().mapKey().equals(mapKey)
                    || !visible.dimension().equals(publication.target().dimension())
                    || !visible.documentId().equals(publication.target().documentId())
                    || visible.revision() != publication.pointer().revision()
                    || !visible.sourceHash().equals(descriptor.sourceHash())
                    || !visible.runtimeHash().equals(descriptor.runtimeHash())) {
                throw new IllegalStateException(
                        "Acceptance minimap authority is internally inconsistent");
            }
        }
        if (current.isPresent()) {
            CurrentResetResult reset = repository.compareAndResetCurrent(
                    mapKey, current.orElseThrow().pointer());
            if (reset == CurrentResetResult.MISMATCH) {
                throw new IllegalStateException(
                        "Acceptance minimap CURRENT changed during stale cleanup");
            }
        }
        if (binding.isPresent()) {
            MinimapCapability.BindingClearResult cleared = bindingStore.compareAndClear(
                    mapKey, binding.orElseThrow());
            if (cleared != MinimapCapability.BindingClearResult.CLEARED
                    && cleared != MinimapCapability.BindingClearResult.ALREADY_ABSENT) {
                throw new IllegalStateException(
                        "Acceptance minimap binding changed during stale cleanup");
            }
        }
        if (current.isPresent() && repository.currentPublication(mapKey).isPresent()) {
            throw new IllegalStateException(
                    "Acceptance minimap CURRENT remained after stale cleanup");
        }
        if (binding.isPresent() && bindingStore.read(mapKey).isPresent()) {
            throw new IllegalStateException(
                    "Acceptance minimap binding remained after stale cleanup");
        }
        if ((current.isPresent() || binding.isPresent()) && FPSMCore.initialized()) {
            FPSMCore.getInstance().getFPSMDataManager().saveAllData();
        }
    }

    static boolean isAcceptanceDocument(NamespacedId documentId, MapKey mapKey) {
        String prefix = "acceptance_" + mapKey.gameType() + "_";
        String path = documentId.path();
        if (!"blockoffensive".equals(documentId.namespace())
                || !path.startsWith(prefix)
                || path.length() != prefix.length() + 32) {
            return false;
        }
        String run = path.substring(prefix.length());
        if (!run.matches("[0-9a-f]{32}")) {
            return false;
        }
        try {
            UUID.fromString(run.substring(0, 8) + "-" + run.substring(8, 12) + "-"
                    + run.substring(12, 16) + "-" + run.substring(16, 20) + "-"
                    + run.substring(20));
            return true;
        } catch (IllegalArgumentException invalidRun) {
            return false;
        }
    }

    public static BOMinimapAcceptanceFixture prepare(
            MinecraftServer server,
            ServerPlayer actor,
            String requestedGameType
    ) {
        // The public factory is callable outside the gated command path.
        BOMinimapAcceptanceEnvironment.requireAvailable(FMLEnvironment.production);
        if (server == null || actor == null) {
            throw new IllegalArgumentException("Acceptance fixture requires a server player");
        }
        if (!"cs".equals(requestedGameType) && !"csdm".equals(requestedGameType)) {
            throw new IllegalArgumentException("Fixture mode must be cs or csdm");
        }
        if (!FPSMCore.initialized()) {
            throw new IllegalStateException("FPSMatch runtime is not initialized");
        }
        BaseMap map = FPSMCore.getInstance().getMapByPlayerWithSpec(actor)
                .orElseThrow(() -> new IllegalStateException(
                        "Player must be in a CS/CSDM map to create an acceptance fixture"
                ));
        return new BOMinimapAcceptanceFixture(server, actor, map, requestedGameType);
    }

    @Override
    public void initialize() {
        ensureOpen();
        if (initialized) {
            throw new IllegalStateException("Acceptance fixture is already initialized");
        }
        publishNonEmptyDocument();
        assertPermissionDenied();
        assertRevisionRetry();
        assertRuntimeVisibility();
        scene = null;
        sceneRevision = publishedBinding.revision();
        initialized = true;
    }

    public synchronized String status() {
        ensureOpen();
        String binding = bindingStore.read(mapKey)
                .map(value -> "revision=" + value.revision())
                .orElse("unbound");
        long currentRevision = repository.currentPublication(mapKey)
                .map(current -> current.pointer().revision())
                .orElse(0L);
        boolean editorScene = scene == Scene.EDITOR_DIRTY
                || scene == Scene.EDITOR_PUBLISHING
                || scene == Scene.EDITOR_ERROR;
        return BOMinimapAcceptanceStatusDiagnostic.render(
                map.getGameType(),
                map.getMapName(),
                documentSlug,
                binding,
                scene == null ? "none" : scene.commandName(),
                acceptanceLedger.status(),
                acceptanceLedger.acknowledgement(),
                editorScene,
                sceneRevision,
                currentRevision
        );
    }


    public synchronized String showScene(String commandName) {
        ensureOpen();
        requireActiveActor();
        Scene requested = Scene.parse(commandName);
        switch (requested) {
            case HUD_CROWDED -> assertMarkersVisible();
            case TACTICAL -> assertRegionsVisible();
            case EDITOR_DIRTY -> requireDraft();
            case EDITOR_PUBLISHING -> assertStalePublishRejected();
            case EDITOR_ERROR -> assertUnauthorizedPublishRejected();
            case MAP_ROOM_ERROR -> assertMapRoomErrorProjection();
        }
        dispatchScene(requested);
        scene = requested;
        return "scene=" + requested.commandName() + " dispatched; status=pending";
    }

    public static List<String> sceneNames() {
        return List.of(Scene.values()).stream().map(Scene::commandName).toList();
    }

    public boolean matchesMap(BaseMap candidate) {
        return map == candidate;
    }

    public boolean matchesConnection(ServerPlayer candidate) {
        return candidate != null
                && candidate == actor
                && candidate.server == server
                && candidate.connection != null
                && candidate.connection == actorConnection
                && server.getPlayerList().getPlayer(candidate.getUUID()) == candidate;
    }

    public synchronized boolean acceptAck(
            ServerPlayer sender,
            BOMinimapAcceptanceAck acknowledgement
    ) {
        if (closed || cleanupStarted || !matchesConnection(sender) || acknowledgement == null
                || !runId.equals(acknowledgement.fixtureRunId())) {
            return false;
        }
        return acceptanceLedger.acceptAck(sender.getUUID(), acknowledgement);
    }

    public synchronized boolean acceptEditorContext(
            ServerPlayer sender,
            BOMinimapAcceptanceEditorContext editorContext
    ) {
        if (closed || cleanupStarted || !matchesConnection(sender) || editorContext == null
                || !runId.equals(editorContext.fixtureRunId())) {
            return false;
        }
        return acceptanceLedger.acceptEditorContext(sender.getUUID(), editorContext);
    }

    public synchronized void tick() {
        if (!closed && !cleanupStarted) {
            acceptanceLedger.tick(server.getTickCount());
        }
    }

    private void dispatchScene(Scene requested) {
        MinimapCapability.Binding binding = requirePublishedIdentity();
        long currentRevision = repository.currentPublication(mapKey)
                .map(current -> current.pointer().revision())
                .orElse(0L);
        if (binding.revision() != currentRevision) {
            throw new IllegalStateException(
                    "Acceptance scene revision is not the authoritative CURRENT revision"
            );
        }
        long epoch = nextSceneEpoch();
        BOMinimapAcceptanceSceneSignal signal = BOMinimapAcceptanceScenePlan.show(
                requested, actor.getUUID(), runId, epoch, mapKey, dimension,
                documentId, binding
        );
        BOMinimapAcceptanceServerLedger.ScenePolicy policy =
                BOMinimapAcceptanceScenePlan.policy(requested);
        long deadline = Math.addExact((long) server.getTickCount(), ACK_TIMEOUT_TICKS);
        cleanupSignal = BOMinimapAcceptanceScenePlan.clear(signal);
        sceneSender.dispatch(requested, signal, policy, deadline);
        sceneRevision = binding.revision();
        committedSceneEpoch = epoch;
    }

    private long nextSceneEpoch() {
        BOMinimapAcceptanceServerLedger.Status state = acceptanceLedger.status();
        if (state == BOMinimapAcceptanceServerLedger.Status.SEND_UNKNOWN) {
            throw new IllegalStateException(
                    "Acceptance scene delivery is unknown; cleanup is required"
            );
        }
        if (state == BOMinimapAcceptanceServerLedger.Status.ABORTED) {
            return acceptanceLedger.expectedEpoch();
        }
        if (state == BOMinimapAcceptanceServerLedger.Status.RESERVED
                || state == BOMinimapAcceptanceServerLedger.Status.DISPATCHED
                || state == BOMinimapAcceptanceServerLedger.Status.WAITING_ACK
                || state == BOMinimapAcceptanceServerLedger.Status.WAITING_CONTEXT) {
            throw new IllegalStateException("The previous acceptance scene is still pending");
        }
        if (committedSceneEpoch >= BOMinimapAcceptanceEpochGate.MAX_EPOCH) {
            throw new IllegalStateException("Acceptance scene epoch is exhausted");
        }
        return committedSceneEpoch + 1L;
    }

    private void requireActiveActor() {
        if (!initialized || !matchesConnection(actor) || !actor.hasPermissions(2)
                || !FPSMCore.initialized()
                || FPSMCore.getInstance().getMapByPlayerWithSpec(actor)
                .filter(candidate -> candidate == map).isEmpty()) {
            throw new IllegalStateException(
                    "Acceptance fixture actor, connection, permission, or map is no longer active"
            );
        }
    }

    private MinimapCapability.Binding requirePublishedIdentity() {
        return publication.requirePublishedBinding();
    }

    private boolean matchesAuthoritativeEditorContext(
            WireIdentity.EditorContext context
    ) {
        MinimapCapability.Binding fixtureBinding = publishedBinding;
        return BOMinimapAcceptanceEditorAuthority.matches(
                mapKey,
                dimension,
                documentId,
                fixtureBinding,
                () -> bindingStore.read(mapKey),
                candidate -> ForgeMinimapServerRuntimeRegistration
                        .matchesActiveEditorContext(
                                server, actor.getUUID(), candidate
                        ),
                context
        );
    }

    private void publishNonEmptyDocument() {
        BOMinimapAcceptancePublication.Snapshot snapshot = publication.initialize(
                CSGameMinimapRegionSources.fromMap(map).collect(mapKey, "ground"),
                visibleTile(),
                UUID.nameUUIDFromBytes(
                        (documentSlug + ":publish").getBytes(StandardCharsets.UTF_8)));
        seedRevision = snapshot.seedRevision();
        seedSourceHash = snapshot.seedSourceHash();
        draftId = snapshot.draftId();
        session = snapshot.session();
        draftRoot = snapshot.draftRoot();
        publishedBinding = snapshot.publishedBinding();
        map.pullGameInfo(actor);
    }

    private void assertPermissionDenied() {
        UUID unauthorized = UUID.nameUUIDFromBytes(
                (documentSlug + ":unauthorized").getBytes(StandardCharsets.UTF_8)
        );
        try {
            publication.sessions().open(
                    unauthorized,
                    mapKey,
                    dimension,
                    documentId,
                    draftId,
                    seedRevision
            );
            throw new IllegalStateException("Unauthorized editor open unexpectedly succeeded");
        } catch (SessionAccessException denied) {
            if (denied.errorCode() != com.ptcrys.fpsmatch.core.minimap.contract.MinimapErrorCode.UNAUTHORIZED) {
                throw new IllegalStateException(
                        "Unauthorized editor open returned " + denied.errorCode()
                );
            }
        }
    }

    private void assertRevisionRetry() {
        try {
            publication.drafts().requireRoot(draftId, Sha256Digest.of(
                    "stale-root".getBytes(StandardCharsets.UTF_8)
            ));
            throw new IllegalStateException("Stale draft root unexpectedly succeeded");
        } catch (DraftException conflict) {
            if (conflict.errorCode() != com.ptcrys.fpsmatch.core.minimap.contract.MinimapErrorCode.REVISION_CONFLICT) {
                throw new IllegalStateException(
                        "Stale draft root returned " + conflict.errorCode()
                );
            }
        }
        publication.drafts().requireMaterialization(draftId, draftRoot);
    }

    private void assertRuntimeVisibility() {
        assertMarkersVisible();
        assertRegionsVisible();
    }

    private void assertMarkersVisible() {
        MinimapViewerContext viewer = BlockOffensiveMinimapRuntime
                .viewerContext(mapKey, actor.getUUID())
                .orElseThrow(() -> new IllegalStateException(
                        "BO minimap viewer context is unavailable"
                ));
        List<MarkerCandidate> candidates = new ArrayList<>();
        BlockOffensiveMinimapRuntime.markerProviders(mapKey).forEach(provider ->
                candidates.addAll(provider.collect(viewer))
        );
        MinimapVisibilityPolicy policy = BlockOffensiveMinimapRuntime
                .visibilityPolicy(mapKey)
                .orElseThrow(() -> new IllegalStateException(
                        "BO minimap visibility policy is unavailable"
                ));
        List<MarkerSnapshot.Marker> visible = policy.filter(viewer, candidates);
        if (visible.isEmpty()) {
            throw new IllegalStateException(
                    "HUD crowded scene has no authoritative visible markers"
            );
        }
    }

    private void assertRegionsVisible() {
        List<MinimapRegionProvider> providers =
                BlockOffensiveMinimapRuntime.regionProviders(mapKey);
        List<RuntimeRegionDescriptor> regions = providers.stream()
                .flatMap(provider -> provider.collect(mapKey, "ground").stream())
                .toList();
        if (regions.isEmpty()) {
            throw new IllegalStateException(
                    "Tactical scene has no authoritative CS/CSDM regions"
            );
        }
        if (map.getGameType().equals("cs")) {
            CSGameMinimapRegionSources.fromMap(map).collect(mapKey, "ground");
        }
    }

    private void assertStalePublishRejected() {
        MinimapCapability.Binding current = bindingStore.read(mapKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Acceptance binding disappeared before stale publish check"));
        if (current.revision() <= seedRevision) {
            throw new IllegalStateException(
                    "Acceptance binding did not advance beyond seed revision");
        }
        // Each editor scene owns its client surface and closes that surface when
        // the scene changes. Reopen the same draft scope before publishing so a
        // closed client session cannot mask the revision-authority assertion.
        session = publication.sessions().open(
                actor.getUUID(), mapKey, dimension, documentId, draftId, seedRevision
        );
        Sha256 staleRoot = Sha256Digest.of(
                "stale-root".getBytes(StandardCharsets.UTF_8)
        );
        PublishWireMessage.PublishResult result = publication.publish(
                actor.getUUID(),
                UUID.nameUUIDFromBytes(
                        (documentSlug + ":stale").getBytes(StandardCharsets.UTF_8)
                ),
                editorContext(new DraftAck(draftId, 1L, staleRoot))
        );
        if (result.outcome() != WireStatus.PublishOutcome.ABORTED
                || !result.error().map(error -> error.errorCode()
                == com.ptcrys.fpsmatch.core.minimap.contract.MinimapErrorCode.REVISION_CONFLICT.code())
                .orElse(false)) {
            throw new IllegalStateException(
                    "Stale publish was not rejected by revision authority: outcome="
                            + result.outcome() + ", error="
                            + result.error().map(WireStatus.ErrorInfo::detail).orElse("none")
                            + ", code="
                            + result.error().map(WireStatus.ErrorInfo::errorCode)
                            .map(String::valueOf).orElse("none"));
        }
    }

    private void assertUnauthorizedPublishRejected() {
        UUID unauthorized = UUID.nameUUIDFromBytes(
                (documentSlug + ":publish-error").getBytes(StandardCharsets.UTF_8)
        );
        PublishWireMessage.PublishResult result = publication.publish(
                unauthorized,
                UUID.nameUUIDFromBytes(
                        (documentSlug + ":error").getBytes(StandardCharsets.UTF_8)
                ),
                editorContext(new DraftAck(draftId, 1L, draftRoot))
        );
        if (result.outcome() != WireStatus.PublishOutcome.ABORTED) {
            throw new IllegalStateException("Unauthorized publish unexpectedly succeeded");
        }
    }

    private void assertMapRoomErrorProjection() {
        if (MapRoomQueryService.findMap(map.getGameType(), "__bo_acceptance_missing__").isPresent()) {
            throw new IllegalStateException("Map-room missing-map projection unexpectedly resolved");
        }
    }

    private WireIdentity.EditorContext editorContext(DraftAck ack) {
        return new WireIdentity.EditorContext(
                new WireIdentity.ScopeLease(
                        WireIdentity.Scope.EDITOR, 1L, 1L
                ),
                new WireIdentity.DocumentBinding(
                        new WireIdentity.MapTarget(mapKey, dimension),
                        documentId
                ),
                session.sessionId(),
                draftId,
                seedRevision,
                seedSourceHash,
                ack.draftRootHash(),
                ack.ackCursor()
        );
    }

    private OptionalInt permissionLevel(UUID actorId) {
        ServerPlayer player = server.getPlayerList().getPlayer(actorId);
        if (player == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(player.hasPermissions(2) ? 2 : 0);
    }

    private void requireDraft() {
        if (draftId == null || draftRoot == null || session == null) {
            throw new IllegalStateException("Acceptance draft is not active");
        }
        publication.drafts().requireRoot(draftId, draftRoot);
    }

    private void ensureOpen() {
        if (closed || cleanupStarted) {
            throw new IllegalStateException(
                    cleanupStarted
                            ? "Acceptance fixture cleanup has started"
                            : "Acceptance fixture is already cleaned up"
            );
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        cleanupStarted = true;
        sendClearBestEffort();
        publication.close();
        if (!privateCleanupComplete) {
            cleanupPrivateState();
            privateCleanupComplete = true;
        }
        closed = true;
    }

    private void sendClearBestEffort() {
        BOMinimapAcceptanceSceneSignal fixed = cleanupSignal;
        if (fixed == null) {
            return;
        }
        try {
            BlockOffensive.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> actor),
                    new BOMinimapAcceptanceSceneS2CPacket(fixed)
            );
        } catch (RuntimeException failure) {
            FPSMatch.LOGGER.warn(
                    "Acceptance CLEAR delivery failed for run {} epoch {}; "
                            + "persistent cleanup will continue",
                    fixed.fixtureRunId(), fixed.epoch(), failure
            );
        }
    }

    private void cleanupPrivateState() {
        List<RuntimeException> failures = new ArrayList<>();
        try {
            // Revoke the old MATCH_HUD lease only after publication.close() has
            // removed the fixture authority; recreation must not inherit it.
            ForgeMinimapServerRuntimeRegistration.invalidateMap(server, mapKey);
        } catch (RuntimeException failure) {
            failures.add(failure);
        }
        try {
            BlockOffensiveMinimapRuntime.reset(map);
        } catch (RuntimeException failure) {
            failures.add(failure);
        }
        try {
            deleteFixtureRoot();
        } catch (RuntimeException failure) {
            failures.add(failure);
        }
        if (!failures.isEmpty()) {
            IllegalStateException aggregate = new IllegalStateException(
                    "Acceptance fixture private cleanup did not complete"
            );
            failures.forEach(aggregate::addSuppressed);
            throw aggregate;
        }
    }

    private void deleteFixtureRoot() {
        Path normalized = fixtureRoot.toAbsolutePath().normalize();
        Path expected = acceptanceRoot.resolve(runId.toString()).normalize();
        if (!normalized.equals(expected)
                || !normalized.startsWith(acceptanceRoot)
                || !acceptanceRoot.getParent().getFileName().toString().equals("acceptance")) {
            throw new IllegalStateException("Acceptance fixture path ownership is invalid");
        }
        try {
            Path cursor = canonicalRoot;
            if (Files.isSymbolicLink(cursor)) {
                throw new IllegalStateException("Canonical minimap root cannot be a symlink");
            }
            for (Path part : canonicalRoot.relativize(normalized)) {
                cursor = cursor.resolve(part);
                if (Files.isSymbolicLink(cursor)) {
                    throw new IllegalStateException(
                            "Acceptance fixture path cannot contain symlinks: " + cursor
                    );
                }
            }
            if (!Files.exists(normalized)) {
                return;
            }
            Path realCanonical = canonicalRoot.toRealPath();
            Path realAcceptance = acceptanceRoot.toRealPath();
            Path realFixture = normalized.toRealPath();
            Path expectedAcceptance = realCanonical.resolve("acceptance")
                    .resolve(actor.getUUID().toString()).normalize();
            if (!realAcceptance.equals(expectedAcceptance)
                    || !realFixture.equals(realAcceptance.resolve(runId.toString()))) {
                throw new IllegalStateException(
                        "Acceptance fixture real path escaped its canonical owner"
                );
            }
            try (var paths = Files.walk(normalized)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException failure) {
                        throw new IllegalStateException(
                                "Unable to clean acceptance fixture path " + path, failure
                        );
                    }
                });
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to enumerate acceptance fixture root", failure);
        }
    }

    private static byte[] visibleTile() {
        byte[] rgba = new byte[128 * 128 * 4];
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                int offset = (y * 128 + x) * 4;
                boolean grid = x % 16 == 0 || y % 16 == 0;
                boolean road = Math.abs(x - y) <= 4 || Math.abs(x + y - 127) <= 3;
                boolean siteA = x >= 18 && x <= 38 && y >= 18 && y <= 38;
                boolean siteB = x >= 88 && x <= 108 && y >= 88 && y <= 108;
                int red = grid ? 0x31 : 0x20;
                int green = grid ? 0x3B : 0x29;
                int blue = grid ? 0x42 : 0x30;
                if (road) {
                    red = 0x67;
                    green = 0x73;
                    blue = 0x79;
                }
                if (siteA || siteB) {
                    red = siteA ? 0xD4 : 0x5B;
                    green = siteA ? 0xFF : 0xC7;
                    blue = siteA ? 0x72 : 0xFF;
                }
                rgba[offset] = (byte) red;
                rgba[offset + 1] = (byte) green;
                rgba[offset + 2] = (byte) blue;
                rgba[offset + 3] = (byte) 0xFF;
            }
        }
        return CanonicalPngCodecV1.encode(128, 128, rgba);
    }

}
