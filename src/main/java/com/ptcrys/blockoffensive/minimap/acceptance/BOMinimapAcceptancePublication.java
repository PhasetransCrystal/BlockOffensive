package com.ptcrys.blockoffensive.minimap.acceptance;

import com.ptcrys.fpsmatch.common.capability.map.MinimapCapability;
import com.ptcrys.fpsmatch.common.minimap.server.DraftAck;
import com.ptcrys.fpsmatch.common.minimap.server.DraftState;
import com.ptcrys.fpsmatch.common.minimap.server.DraftStore;
import com.ptcrys.fpsmatch.common.minimap.server.EditorSession;
import com.ptcrys.fpsmatch.common.minimap.server.EditorSessionManager;
import com.ptcrys.fpsmatch.common.minimap.server.MinimapBindingCoordinator;
import com.ptcrys.fpsmatch.common.minimap.server.MinimapPermissionPolicy;
import com.ptcrys.fpsmatch.common.minimap.server.ServerEditorPublishService;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorCommandHasher;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorOperation;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.region.RuntimeRegionDescriptor;
import com.ptcrys.fpsmatch.core.minimap.storage.CurrentPublication;
import com.ptcrys.fpsmatch.core.minimap.storage.CurrentPointer;
import com.ptcrys.fpsmatch.core.minimap.storage.CurrentResetResult;
import com.ptcrys.fpsmatch.core.minimap.storage.MinimapRepository;
import com.ptcrys.fpsmatch.core.minimap.storage.PublishOutcome;
import com.ptcrys.fpsmatch.core.minimap.wire.PublishWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;
import com.ptcrys.fpsmatch.core.minimap.wire.WireStatus;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Owns the acceptance-only seed, real editor publish, and exact cleanup transaction. */
final class BOMinimapAcceptancePublication implements AutoCloseable {
    record Scope(
            UUID actorId,
            MapKey mapKey,
            NamespacedId dimension,
            NamespacedId documentId
    ) {
        Scope {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(mapKey, "mapKey");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(documentId, "documentId");
        }
    }

    interface DraftStoreFactory {
        Path root();

        DraftStore open();

        default void afterCreate(DraftState created) {
        }
    }

    record Snapshot(
            long seedRevision,
            Sha256 seedSourceHash,
            UUID draftId,
            EditorSession session,
            Sha256 draftRoot,
            long publishedRevision,
            MinimapCapability.Binding publishedBinding,
            CurrentPublication publishedCurrent
    ) {
        Snapshot {
            if (seedRevision <= 0L || publishedRevision <= seedRevision) {
                throw new IllegalArgumentException("Acceptance publication revisions are invalid");
            }
            Objects.requireNonNull(seedSourceHash, "seedSourceHash");
            Objects.requireNonNull(draftId, "draftId");
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(draftRoot, "draftRoot");
            Objects.requireNonNull(publishedBinding, "publishedBinding");
            Objects.requireNonNull(publishedCurrent, "publishedCurrent");
        }
    }

    private final Scope scope;
    private final MinimapRepository repository;
    private final DraftStoreFactory draftStores;
    private final Path draftStoreRoot;
    private final MinimapPermissionPolicy permissions;
    private final MinimapBindingCoordinator.BindingStore bindingStore;
    private final Clock clock;
    private final BiConsumer<UUID, PublishWireMessage> delivery;
    private final Consumer<MapKey> invalidator;
    private final BOMinimapAcceptanceSourceSeed.RepositoryActions repositoryActions;
    private DraftStore drafts;
    private EditorSessionManager sessions;
    private MinimapBindingCoordinator bindings;
    private ServerEditorPublishService publisher;
    private PublishRequest activeRequest;
    private PublishWireMessage.PublishResult activeResult;
    private PublishAttempt finalAttempt;
    private BOMinimapAcceptanceSourceSeed.Attempt seedAttempt;
    private CurrentPublication seedCurrent;
    private MinimapCapability.Binding seedBinding;
    private CurrentPublication finalCurrent;
    private MinimapCapability.Binding finalBinding;
    private long seedRevision;
    private Sha256 seedSourceHash;
    private UUID draftId;
    private EditorSession session;
    private Sha256 draftRoot;
    private boolean draftStoreOpened;
    private boolean privateDraftRootOwned;
    private boolean draftLifecycleStarted;
    private boolean repositoryRecoveryRequired;
    private boolean initialized;
    private boolean cleanupStarted;
    private boolean finalBindingReleased;
    private boolean seedBindingReleased;
    private boolean finalCurrentReleased;
    private boolean seedCurrentReleased;
    private boolean sessionReleased;
    private boolean draftReleased;
    private boolean closed;

    BOMinimapAcceptancePublication(
            Scope scope,
            MinimapRepository repository,
            DraftStoreFactory draftStores,
            MinimapPermissionPolicy permissions,
            MinimapBindingCoordinator.BindingStore bindingStore,
            Clock clock,
            BiConsumer<UUID, PublishWireMessage> delivery,
            Consumer<MapKey> invalidator
    ) {
        this(
                scope, repository, draftStores, permissions, bindingStore, clock,
                delivery, invalidator, defaultRepositoryActions(repository));
    }

    BOMinimapAcceptancePublication(
            Scope scope,
            MinimapRepository repository,
            DraftStoreFactory draftStores,
            MinimapPermissionPolicy permissions,
            MinimapBindingCoordinator.BindingStore bindingStore,
            Clock clock,
            BiConsumer<UUID, PublishWireMessage> delivery,
            Consumer<MapKey> invalidator,
            BOMinimapAcceptanceSourceSeed.RepositoryActions repositoryActions
    ) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.draftStores = Objects.requireNonNull(draftStores, "draftStores");
        this.draftStoreRoot = normalizeDraftRoot(draftStores.root());
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.bindingStore = Objects.requireNonNull(bindingStore, "bindingStore");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.delivery = Objects.requireNonNull(delivery, "delivery");
        this.invalidator = Objects.requireNonNull(invalidator, "invalidator");
        this.repositoryActions = Objects.requireNonNull(
                repositoryActions, "repositoryActions");
    }

    synchronized Snapshot initialize(
            List<RuntimeRegionDescriptor> liveRegions,
            byte[] visibleTile,
            UUID publishRequestId
    ) {
        ensureNew();
        List<RuntimeRegionDescriptor> regions = List.copyOf(liveRegions);
        byte[] tile = Objects.requireNonNull(visibleTile, "visibleTile").clone();
        UUID requestId = Objects.requireNonNull(publishRequestId, "publishRequestId");
        try {
            requirePristineDraftRoot();
            privateDraftRootOwned = true;
            drafts = openDraftStore();
            draftStoreOpened = true;
            requireDraftRootEmpty();
            sessions = new EditorSessionManager(
                    permissions, () -> Duration.ofMinutes(10), clock);
            bindings = new MinimapBindingCoordinator(bindingStore);
            publisher = new ServerEditorPublishService(
                    repository, sessions, permissions, drafts, bindings,
                    this::acceptDelivery, invalidator);

            BOMinimapAcceptanceSourceSeed.Publication seed =
                    BOMinimapAcceptanceSourceSeed.publish(
                            repository, scope.mapKey(), scope.dimension(),
                            scope.documentId(), regions,
                            attempt -> seedAttempt = attempt, repositoryActions);
            seedCurrent = seed.current();
            seedBinding = bindingFor(seedCurrent);
            seedRevision = seed.revision();
            seedSourceHash = seed.sourceHash();
            if (!bindings.bindCommitted(scope.mapKey(), null, seedBinding)) {
                throw new IllegalStateException(
                        "Acceptance seed binding is awaiting recovery");
            }

            draftLifecycleStarted = true;
            DraftState created = drafts.create(
                    scope.mapKey(), scope.dimension(), scope.documentId(),
                    seedRevision, seedSourceHash, ServerEditorPublishService.emptyHash());
            draftStores.afterCreate(created);
            Set<UUID> createdIds = strictDraftIds();
            if (!createdIds.equals(Set.of(created.draftId()))) {
                throw new IllegalStateException(
                        "Acceptance draft root does not contain exactly the created draft");
            }
            draftId = created.draftId();
            session = sessions.open(
                    scope.actorId(), scope.mapKey(), scope.dimension(),
                    scope.documentId(), draftId, seedRevision);
            draftRoot = applyVisibleTile(tile).draftRootHash();
            WireIdentity.EditorContext context = editorContext();

            PublishWireMessage.PublishResult result;
            try {
                result = publish(scope.actorId(), requestId, context);
            } catch (RuntimeException failure) {
                rememberFinalAttempt(requestId, context);
                throw failure;
            }
            rememberFinalAttempt(requestId, context);
            recoverPublishedAuthority();
            if (result.outcome() != WireStatus.PublishOutcome.COMMITTED) {
                throw new IllegalStateException(
                        "Authoritative fixture publish was rejected: "
                                + result.error().map(WireStatus.ErrorInfo::detail)
                                .orElse("unknown"));
            }
            requireReadyFinal(result);
            initialized = true;
            return snapshot();
        } catch (RuntimeException failure) {
            try {
                recoverPublishedAuthority();
            } catch (RuntimeException recoveryFailure) {
                failure.addSuppressed(recoveryFailure);
            }
            throw failure;
        }
    }

    synchronized PublishWireMessage.PublishResult publish(
            UUID actorId,
            UUID requestId,
            WireIdentity.EditorContext context
    ) {
        ensureOperational();
        PublishRequest request = new PublishRequest(
                Objects.requireNonNull(actorId, "actorId"),
                new PublishWireMessage.ReservePublish(
                        Objects.requireNonNull(requestId, "requestId"),
                        Objects.requireNonNull(context, "context")));
        if (activeRequest != null) {
            throw new IllegalStateException("Acceptance publish is already active");
        }
        activeRequest = request;
        activeResult = null;
        try {
            publisher.publish(actorId, request.message());
            return requireActiveResult();
        } finally {
            activeRequest = null;
        }
    }

    synchronized DraftStore drafts() {
        if (drafts == null) {
            throw new IllegalStateException("Acceptance draft store is unavailable");
        }
        return drafts;
    }

    synchronized EditorSessionManager sessions() {
        if (sessions == null) {
            throw new IllegalStateException("Acceptance editor sessions are unavailable");
        }
        return sessions;
    }

    synchronized Snapshot snapshot() {
        if (draftId == null || session == null || draftRoot == null
                || finalBinding == null || finalCurrent == null) {
            throw new IllegalStateException("Acceptance publication is not ready");
        }
        return new Snapshot(
                seedRevision, seedSourceHash, draftId, session, draftRoot,
                finalBinding.revision(), finalBinding, finalCurrent);
    }

    synchronized MinimapCapability.Binding requirePublishedBinding() {
        if (!initialized || cleanupStarted || closed
                || finalBinding == null || finalCurrent == null) {
            throw new IllegalStateException("Acceptance publication is unavailable");
        }
        CurrentPublication current = repository.currentPublication(scope.mapKey())
                .orElseThrow(() -> new IllegalStateException(
                        "Acceptance publication CURRENT is unavailable"));
        if (!current.pointer().equals(finalCurrent.pointer())
                || !bindingFor(current).equals(finalBinding)
                || bindingStore.read(scope.mapKey()).filter(finalBinding::equals).isEmpty()) {
            throw new IllegalStateException(
                    "Acceptance minimap binding no longer matches CURRENT");
        }
        return finalBinding;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        cleanupStarted = true;
        recoverPublishedAuthority();
        if (bindings != null && (!finalBindingReleased || !seedBindingReleased)) {
            bindings.recoverPending();
        }
        seedBindingReleased = releaseBinding(seedBinding, seedBindingReleased);
        finalBindingReleased = releaseBinding(finalBinding, finalBindingReleased);
        finalCurrentReleased = releaseCurrent(finalCurrent, finalCurrentReleased);
        seedCurrentReleased = releaseCurrent(seedCurrent, seedCurrentReleased);
        if (!sessionReleased) {
            if (sessions != null) {
                sessions.invalidateActor(scope.actorId());
            }
            sessionReleased = true;
        }
        if (!draftReleased) {
            releaseAllDrafts();
            draftReleased = true;
        }
        publisher = null;
        bindings = null;
        sessions = null;
        drafts = null;
        activeRequest = null;
        activeResult = null;
        closed = true;
    }

    private void acceptDelivery(UUID actorId, PublishWireMessage message) {
        PublishRequest request = activeRequest;
        if (request == null || !request.actorId().equals(actorId)
                || !(message instanceof PublishWireMessage.PublishResult result)
                || !matchesRequest(result, request.message())) {
            throw new IllegalStateException(
                    "Acceptance publisher returned an uncorrelated result");
        }
        activeResult = result;
        delivery.accept(actorId, message);
    }

    private PublishWireMessage.PublishResult requireActiveResult() {
        if (activeResult == null) {
            throw new IllegalStateException(
                    "Authoritative publish service returned no result");
        }
        return activeResult;
    }

    private void rememberFinalAttempt(
            UUID requestId,
            WireIdentity.EditorContext context
    ) {
        PublishWireMessage.PublishResult result = activeResult;
        if (result == null) {
            return;
        }
        PublishWireMessage.ReservePublish request =
                new PublishWireMessage.ReservePublish(requestId, context);
        if (!matchesRequest(result, request)) {
            throw new IllegalStateException("Acceptance final result is uncorrelated");
        }
        finalAttempt = new PublishAttempt(request, result);
    }

    private void recoverPublishedAuthority() {
        PublishOutcome recovery = repository.recover(scope.mapKey());
        if (recovery.status() == PublishOutcome.Status.UNAVAILABLE
                || recovery.status() == PublishOutcome.Status.COMMIT_STATUS_UNKNOWN) {
            throw new IllegalStateException(
                    "Acceptance repository recovery did not converge: "
                            + recovery.message());
        }
        Optional<CurrentPublication> current =
                repository.currentPublication(scope.mapKey());
        if (current.isEmpty()) {
            return;
        }
        CurrentPublication candidate = current.orElseThrow();
        if (matchesFinalAttempt(candidate)) {
            finalCurrent = candidate;
            finalBinding = bindingFor(candidate);
        }
    }

    private boolean matchesFinalAttempt(CurrentPublication publication) {
        PublishAttempt attempt = finalAttempt;
        if (attempt == null || seedCurrent == null) {
            return false;
        }
        PublishWireMessage.PublishResult result = attempt.result();
        var descriptor = publication.record().descriptor();
        if (!publication.target().mapKey().equals(scope.mapKey())
                || !publication.target().dimension().equals(scope.dimension())
                || !publication.target().documentId().equals(scope.documentId())
                || descriptor.baseRevision() != seedRevision
                || descriptor.publishRevision() != result.publishRevision()
                || !descriptor.publishToken().equals(result.publishToken())) {
            return false;
        }
        return result.hashes().map(hashes ->
                hashes.sourceHash().equals(descriptor.sourceHash())
                        && hashes.runtimeHash().equals(descriptor.runtimeHash())
                        && hashes.runtimeContainerHash()
                        .equals(descriptor.runtimeContainerHash())
        ).orElse(true);
    }

    private void requireReadyFinal(PublishWireMessage.PublishResult result) {
        if (finalCurrent == null || finalBinding == null
                || finalBinding.revision() != result.publishRevision()
                || bindingStore.read(scope.mapKey()).filter(finalBinding::equals).isEmpty()) {
            throw new IllegalStateException(
                    "Acceptance final publication is not visible");
        }
    }

    private DraftAck applyVisibleTile(byte[] tile) {
        Sha256 hash = com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest.of(tile);
        EditorOperation.PutTile operation = new EditorOperation.PutTile(
                "ground", "paint", 0, 0, Optional.empty(), hash);
        byte[] descriptor = EditorCommandHasher.descriptorBytes(List.of(operation));
        return drafts.apply(
                draftIdFromStore(), ServerEditorPublishService.emptyHash(), 1L,
                com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest.of(descriptor),
                descriptor, Map.of(hash, tile));
    }

    private UUID draftIdFromStore() {
        Set<UUID> ids = strictDraftIds();
        if (ids.size() != 1) {
            throw new IllegalStateException("Acceptance draft identity is unavailable");
        }
        return ids.iterator().next();
    }

    private WireIdentity.EditorContext editorContext() {
        return new WireIdentity.EditorContext(
                new WireIdentity.ScopeLease(WireIdentity.Scope.EDITOR, 1L, 1L),
                new WireIdentity.DocumentBinding(
                        new WireIdentity.MapTarget(scope.mapKey(), scope.dimension()),
                        scope.documentId()),
                session.sessionId(), draftId, seedRevision, seedSourceHash,
                draftRoot, 1L);
    }

    private boolean releaseBinding(
            MinimapCapability.Binding candidate,
            boolean alreadyReleased
    ) {
        if (alreadyReleased || candidate == null) {
            return true;
        }
        if (bindings == null) {
            throw new IllegalStateException("Acceptance binding coordinator is unavailable");
        }
        MinimapCapability.BindingClearResult result =
                bindings.compareAndClear(scope.mapKey(), candidate);
        return switch (result) {
            case CLEARED, ALREADY_ABSENT, MISMATCH -> true;
            case UNAVAILABLE -> throw new IllegalStateException(
                    "Acceptance binding cleanup is unavailable");
        };
    }

    private boolean releaseCurrent(
            CurrentPublication candidate,
            boolean alreadyReleased
    ) {
        if (alreadyReleased || candidate == null) {
            return true;
        }
        CurrentResetResult result = repository.compareAndResetCurrent(
                scope.mapKey(), candidate.pointer());
        return switch (result) {
            case RESET, ALREADY_RESET, MISMATCH -> true;
        };
    }

    private void releaseAllDrafts() {
        if (!draftStoreOpened && !draftLifecycleStarted) {
            return;
        }
        DraftStore recovered = openDraftStore();
        List<UUID> ids = strictDraftIds().stream()
                .sorted(Comparator.comparing(UUID::toString)).toList();
        for (UUID id : ids) {
            recovered.discard(id);
        }
        drafts = openDraftStore();
        requireDraftRootEmpty();
    }

    private DraftStore openDraftStore() {
        if (!normalizeDraftRoot(draftStores.root()).equals(draftStoreRoot)) {
            throw new IllegalStateException("Acceptance draft root changed");
        }
        requireNoSymlinkComponents(draftStoreRoot);
        DraftStore opened = Objects.requireNonNull(
                draftStores.open(), "draft store factory returned null");
        requireNoSymlinkComponents(draftStoreRoot);
        return opened;
    }

    private void requirePristineDraftRoot() {
        Path fixtureRoot = draftStoreRoot.getParent();
        if (fixtureRoot == null) {
            throw new IllegalStateException("Acceptance fixture root is unavailable");
        }
        Path ownerRoot = fixtureRoot.getParent();
        if (ownerRoot == null) {
            throw new IllegalStateException("Acceptance owner root is unavailable");
        }
        requireNoSymlinkComponents(ownerRoot);
        try {
            Files.createDirectories(ownerRoot);
            requireNoSymlinkComponents(ownerRoot);
            Files.createDirectory(fixtureRoot);
        } catch (java.nio.file.FileAlreadyExistsException failure) {
            throw new IllegalStateException(
                    "Acceptance fixture root already exists", failure);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(
                    "Unable to acquire acceptance fixture root", failure);
        }
        requireNoSymlinkComponents(fixtureRoot);
    }

    private void requireDraftRootEmpty() {
        if (!strictDraftIds().isEmpty()) {
            throw new IllegalStateException("Acceptance draft root is not empty");
        }
        try (var entries = Files.list(draftStoreRoot)) {
            if (entries.findAny().isPresent()) {
                throw new IllegalStateException(
                        "Acceptance draft root contains an unknown entry");
            }
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Unable to inspect acceptance draft root", failure);
        }
    }

    private Set<UUID> strictDraftIds() {
        if (!Files.exists(draftStoreRoot, LinkOption.NOFOLLOW_LINKS)) {
            return Set.of();
        }
        if (Files.isSymbolicLink(draftStoreRoot)
                || !Files.isDirectory(draftStoreRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Acceptance draft root is not a real directory");
        }
        java.util.LinkedHashSet<UUID> ids = new java.util.LinkedHashSet<>();
        try (var entries = Files.list(draftStoreRoot)) {
            for (Path entry : entries.toList()) {
                if (Files.isSymbolicLink(entry)
                        || !Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalStateException(
                            "Acceptance draft root contains an unknown entry: " + entry);
                }
                UUID id;
                try {
                    id = UUID.fromString(entry.getFileName().toString());
                } catch (IllegalArgumentException failure) {
                    throw new IllegalStateException(
                            "Acceptance draft root contains a non-UUID directory: " + entry,
                            failure);
                }
                if (!id.toString().equals(entry.getFileName().toString()) || !ids.add(id)) {
                    throw new IllegalStateException(
                            "Acceptance draft root contains an invalid UUID directory: " + entry);
                }
            }
            return Set.copyOf(ids);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Unable to enumerate acceptance drafts", failure);
        }
    }

    private static Path normalizeDraftRoot(Path root) {
        Path normalized = Objects.requireNonNull(root, "draft root")
                .toAbsolutePath().normalize();
        if (normalized.getFileName() == null
                || !normalized.getFileName().toString().equals("drafts")) {
            throw new IllegalArgumentException("Acceptance draft root must end in drafts");
        }
        return normalized;
    }

    private static void requireNoSymlinkComponents(Path path) {
        Path cursor = path.getRoot();
        if (cursor == null) {
            throw new IllegalStateException("Acceptance draft root is not absolute");
        }
        for (Path part : path) {
            cursor = cursor.resolve(part);
            if (Files.isSymbolicLink(cursor)) {
                throw new IllegalStateException(
                        "Acceptance draft root cannot contain symlinks: " + cursor);
            }
        }
    }

    private static boolean matchesRequest(
            PublishWireMessage.PublishResult result,
            PublishWireMessage.ReservePublish request
    ) {
        return result.requestId().equals(request.requestId())
                && result.lease().equals(request.context().lease())
                && result.binding().equals(request.context().binding());
    }

    private static MinimapCapability.Binding bindingFor(CurrentPublication publication) {
        var descriptor = publication.record().descriptor();
        return new MinimapCapability.Binding(
                publication.target().dimension(), publication.target().documentId(),
                descriptor.publishRevision(), descriptor.sourceHash(), descriptor.runtimeHash());
    }

    private static BOMinimapAcceptanceSourceSeed.RepositoryActions defaultRepositoryActions(
            MinimapRepository repository
    ) {
        return new BOMinimapAcceptanceSourceSeed.RepositoryActions() {
            @Override
            public PublishOutcome commit(
                    com.ptcrys.fpsmatch.core.minimap.storage.PublishTransaction transaction
            ) {
                return repository.commit(transaction);
            }

            @Override
            public PublishOutcome recover(MapKey mapKey) {
                return repository.recover(mapKey);
            }
        };
    }

    private void ensureNew() {
        if (initialized || cleanupStarted || closed || drafts != null) {
            throw new IllegalStateException("Acceptance publication cannot be initialized");
        }
    }

    private void ensureOperational() {
        if (publisher == null || cleanupStarted || closed) {
            throw new IllegalStateException("Acceptance publication is not operational");
        }
    }

    private record PublishRequest(
            UUID actorId,
            PublishWireMessage.ReservePublish message
    ) {
    }

    private record PublishAttempt(
            PublishWireMessage.ReservePublish request,
            PublishWireMessage.PublishResult result
    ) {
    }
}
