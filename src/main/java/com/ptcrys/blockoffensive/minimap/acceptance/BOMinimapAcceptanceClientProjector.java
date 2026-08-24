package com.ptcrys.blockoffensive.minimap.acceptance;

import com.ptcrys.blockoffensive.BlockOffensive;
import com.ptcrys.blockoffensive.client.screen.hud.CSGameHud;
import com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceAck;
import com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceAckC2SPacket;
import com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceEditorContext;
import com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceEditorContextC2SPacket;
import com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceEpochGate;
import com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceSceneSignal;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.client.FPSMClient;
import com.ptcrys.fpsmatch.common.client.FPSMGameHudManager;
import com.ptcrys.fpsmatch.common.client.minimap.RuntimeGeneration;
import com.ptcrys.fpsmatch.common.client.minimap.editor.EditorStatus;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.MinimapClientScreens.TacticalDiagnosticSnapshot;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.MinimapClientScreens.TacticalDiagnosticStage;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.MinimapClientScreens.TacticalExceptionBoundary;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.TacticalOpenRequest;
import com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2.Ldlib2MinimapHudPresentation;
import com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2.editor.MinimapEditorScreens;
import com.ptcrys.fpsmatch.common.client.net.FPSMClientPacketRegistrar;
import com.ptcrys.fpsmatch.common.client.screen.mapselect.FPSMMapSelectScreens;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomMinimapIdentity;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomSummary;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomToastS2CPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapSelectionSnapshotS2CPacket;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Projects correlated acceptance scenes onto real client surfaces. */
public final class BOMinimapAcceptanceClientProjector {
    static final String TACTICAL_DIAGNOSTIC_TAG =
            "BO_MINIMAP_ACCEPTANCE_TACTICAL_DIAGNOSTIC";
    static final String EDITOR_DIAGNOSTIC_TAG =
            "BO_MINIMAP_ACCEPTANCE_EDITOR_DIAGNOSTIC";
    private static final int MAX_PENDING_TICKS = 100;
    private static final AtomicReference<Projection> STATE = new AtomicReference<>();
    private static final BOMinimapAcceptanceEpochGate EPOCHS =
            new BOMinimapAcceptanceEpochGate();

    private BOMinimapAcceptanceClientProjector() {
    }

    public static long connectionGeneration() {
        return EPOCHS.connectionGeneration();
    }

    public static synchronized void accept(
            BOMinimapAcceptanceSceneSignal signal,
            long capturedGeneration
    ) {
        Objects.requireNonNull(signal, "signal");
        if (capturedGeneration != connectionGeneration()) {
            return;
        }
        BOMinimapAcceptanceEpochGate.Decision decision = EPOCHS.accept(
                signal.action(), signal.ownerId(), signal.fixtureRunId(), signal.epoch()
        );
        if (decision != BOMinimapAcceptanceEpochGate.Decision.APPLY) {
            return;
        }

        if (signal.action() == BOMinimapAcceptanceSceneSignal.Action.CLEAR) {
            clearAcceptedSignal(signal, capturedGeneration);
            return;
        }

        Projection previous = STATE.getAndSet(null);
        Projection projection = new Projection(capturedGeneration, signal);
        runIdempotentCleanup(
                previous == null ? new AtomicBoolean() : previous.teardownStarted,
                () -> {
                    if (previous != null) {
                        closeOwnedSurface(previous);
                    }
                },
                () -> STATE.set(projection)
        );
        advance(projection);
    }

    public static synchronized void tick() {
        Projection projection = STATE.get();
        if (projection == null || projection.connectionGeneration != connectionGeneration()
                || projection.acknowledged) {
            return;
        }
        if (advance(projection)) {
            return;
        }
        projection.pendingTicks++;
        if (projection.pendingTicks < MAX_PENDING_TICKS) {
            return;
        }

        BOMinimapAcceptanceSceneSignal.Scene scene = projection.signal.scene();
        String detail = timeoutDetail(
                scene,
                projection.tacticalDiagnostic,
                projection.projectorException,
                projection.editorDiagnostic
        );
        if (STATE.compareAndSet(projection, null)) {
            EPOCHS.markClosed(
                    projection.signal.ownerId(),
                    projection.signal.fixtureRunId(),
                    projection.signal.epoch()
            );
            projection.acknowledged = true;
            if (scene == BOMinimapAcceptanceSceneSignal.Scene.TACTICAL) {
                runTacticalTimeout(
                        detail,
                        value -> FPSMatch.LOGGER.error(
                                "{} {}", TACTICAL_DIAGNOSTIC_TAG, value
                        ),
                        () -> closeOwnedSurface(projection),
                        value -> acknowledge(
                                projection.signal,
                                BOMinimapAcceptanceAck.Outcome.TIMED_OUT,
                                value
                        )
                );
            } else {
                logEditorTimeout(projection);
                runCleanupBestEffort(
                        () -> closeOwnedSurface(projection),
                        () -> acknowledge(
                                projection.signal,
                                BOMinimapAcceptanceAck.Outcome.TIMED_OUT,
                                detail
                        )
                );
            }
        }
    }

    private static void logEditorTimeout(Projection projection) {
        String detail = timeoutDetail(
                projection.signal.scene(),
                projection.tacticalDiagnostic,
                projection.projectorException,
                projection.editorDiagnostic
        );
        FPSMatch.LOGGER.error("{} {}", EDITOR_DIAGNOSTIC_TAG, detail);
    }

    public static synchronized void manualClose(Screen screen) {
        Objects.requireNonNull(screen, "screen");
        Projection projection = STATE.get();
        if (projection == null || projection.connectionGeneration != connectionGeneration()
                || !ownsScreen(projection, screen)
                || !STATE.compareAndSet(projection, null)) {
            return;
        }
        EPOCHS.markClosed(
                projection.signal.ownerId(),
                projection.signal.fixtureRunId(),
                projection.signal.epoch()
        );
        if (!projection.acknowledged) {
            projection.acknowledged = true;
            runIdempotentCleanup(
                    projection.teardownStarted,
                    () -> closeOwnedSurface(projection),
                    () -> acknowledge(
                            projection.signal,
                            BOMinimapAcceptanceAck.Outcome.CLOSED,
                            "surface-closed"
                    )
            );
        } else {
            runIdempotentCleanup(
                    projection.teardownStarted,
                    () -> closeOwnedSurface(projection),
                    () -> {
                    }
            );
        }
    }

    public static synchronized void clear(UUID ownerId, UUID fixtureRunId, long epoch) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(fixtureRunId, "fixtureRunId");
        Projection projection = STATE.get();
        if (projection != null && projection.matches(ownerId, fixtureRunId, epoch)
                && STATE.compareAndSet(projection, null)) {
            runIdempotentCleanup(
                    projection.teardownStarted,
                    () -> closeOwnedSurface(projection),
                    () -> {
                    }
            );
        }
        EPOCHS.markClosed(ownerId, fixtureRunId, epoch);
    }

    public static synchronized void resetConnection() {
        Projection projection = STATE.getAndSet(null);
        EPOCHS.resetConnection();
        if (projection != null) {
            runIdempotentCleanup(
                    projection.teardownStarted,
                    () -> closeOwnedSurface(projection),
                    () -> {
                    }
            );
        }
    }

    public static Optional<State> state() {
        Projection projection = STATE.get();
        if (projection == null) {
            return Optional.empty();
        }
        return Optional.of(new State(
                projection.connectionGeneration,
                projection.signal.ownerId(),
                projection.signal.fixtureRunId(),
                projection.signal.epoch(),
                projection.signal,
                projection.visible
        ));
    }

    private static void clearAcceptedSignal(
            BOMinimapAcceptanceSceneSignal signal,
            long capturedGeneration
    ) {
        Projection projection = STATE.get();
        if (projection == null || projection.connectionGeneration != capturedGeneration
                || !projection.matches(
                signal.ownerId(), signal.fixtureRunId(), signal.epoch()
        ) || !STATE.compareAndSet(projection, null)) {
            return;
        }
        if (!projection.acknowledged) {
            projection.acknowledged = true;
            runIdempotentCleanup(
                    projection.teardownStarted,
                    () -> closeOwnedSurface(projection),
                    () -> acknowledge(
                            signal,
                            BOMinimapAcceptanceAck.Outcome.CLOSED,
                            "scene-cleared"
                    )
            );
        } else {
            runIdempotentCleanup(
                    projection.teardownStarted,
                    () -> closeOwnedSurface(projection),
                    () -> {
                    }
            );
        }
    }

    private static boolean advance(Projection projection) {
        if (STATE.get() != projection
                || projection.connectionGeneration != connectionGeneration()) {
            return false;
        }
        try {
            boolean ready = switch (projection.signal.scene()) {
                case HUD_CROWDED -> projectHud(projection);
                case TACTICAL -> projectTactical(projection);
                case EDITOR_DIRTY, EDITOR_PUBLISHING, EDITOR_ERROR ->
                        projectEditor(projection);
                case MAP_ROOM_ERROR -> projectMapRoom(projection);
            };
            if (ready) {
                finishApplied(projection);
            }
            return ready;
        } catch (RuntimeException failure) {
            projection.projectorException = firstProjectorException(
                    projection.projectorException, failure
            );
            return false;
        }
    }

    private static boolean projectHud(Projection projection) {
        RuntimeGeneration generation = captureRuntimeGeneration(projection);
        long frameSequence = FPSMGameHudManager.INSTANCE
                .currentRenderFrameSequence();
        if (generation == null
                || CSGameHud.getInstance().currentFrameGeometry() == null
                || CSGameHud.getInstance().currentFrameGeometrySequence()
                != frameSequence) {
            return false;
        }
        Ldlib2MinimapHudPresentation presentation =
                FPSMGameHudManager.INSTANCE.minimapHudPresentation();
        return presentation != null
                && presentation.acceptanceFrameState(
                generation, frameSequence
        ).isPresent();
    }

    private static boolean projectTactical(Projection projection) {
        RuntimeGeneration generation = captureRuntimeGeneration(projection);
        if (generation == null) {
            return false;
        }
        if (!projection.surfaceOpened) {
            if (FPSMClientPacketRegistrar.openAcceptanceTactical(
                    generation,
                    new TacticalOpenRequest(true, true, false, false)
            )) {
                projection.surfaceOpened = true;
            }
        }
        FPSMClientPacketRegistrar.AcceptanceTacticalState state =
                FPSMClientPacketRegistrar.acceptanceTacticalState(generation);
        TacticalDiagnosticSnapshot diagnostic = FPSMClientPacketRegistrar
                .acceptanceTacticalDiagnostic(generation).orElse(null);
        projection.tacticalDiagnostic = diagnostic;
        Screen screen = Minecraft.getInstance().screen;
        boolean owned = FPSMClientPacketRegistrar.ownsAcceptanceTacticalScreen(
                generation, screen
        );
        return tacticalReady(state, diagnostic, owned);
    }

    static boolean tacticalReady(
            FPSMClientPacketRegistrar.AcceptanceTacticalState state,
            TacticalDiagnosticSnapshot diagnostic,
            boolean ownsCurrentScreen
    ) {
        return state == FPSMClientPacketRegistrar.AcceptanceTacticalState.READY
                && diagnostic != null
                && diagnostic.stage() == TacticalDiagnosticStage.SCREEN_OWNED
                && diagnostic.stage().terminal()
                && ownsCurrentScreen;
    }

    static String timeoutDetail(
            BOMinimapAcceptanceSceneSignal.Scene scene,
            TacticalDiagnosticSnapshot diagnostic,
            String projectorException
    ) {
        return timeoutDetail(scene, diagnostic, projectorException, null);
    }

    static String timeoutDetail(
            BOMinimapAcceptanceSceneSignal.Scene scene,
            TacticalDiagnosticSnapshot diagnostic,
            String projectorException,
            String editorDiagnostic
    ) {
        Objects.requireNonNull(scene, "scene");
        if (scene != BOMinimapAcceptanceSceneSignal.Scene.TACTICAL) {
            if (editorDiagnostic != null && !editorDiagnostic.isEmpty()) {
                return "surface-readiness-timeout:editor:"
                        + sanitizedEditorDiagnostic(editorDiagnostic);
            }
            return "surface-readiness-timeout";
        }
        if (diagnostic != null && diagnostic.stage().terminal()) {
            return tacticalTimeoutDetail(diagnostic);
        }
        if (projectorException != null) {
            return runtimeExceptionDetail(
                    TacticalExceptionBoundary.PROJECTOR, projectorException
            );
        }
        if (diagnostic != null) {
            return tacticalTimeoutDetail(diagnostic);
        }
        return "surface-readiness-timeout:runtime_unavailable";
    }

    private static String sanitizedEditorDiagnostic(String value) {
        if (value == null || value.isEmpty() || value.length() > 1024) {
            return "unknown";
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(character >= 'A' && character <= 'Z')
                    && !(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9')
                    && character != '.'
                    && character != '_'
                    && character != '$'
                    && character != ':') {
                return "unknown";
            }
        }
        return value;
    }

    static String tacticalTimeoutDetail(TacticalDiagnosticSnapshot diagnostic) {
        Objects.requireNonNull(diagnostic, "diagnostic");
        if (diagnostic.stage() == TacticalDiagnosticStage.RUNTIME_EXCEPTION) {
            return runtimeExceptionDetail(
                    diagnostic.exceptionBoundary(), diagnostic.exceptionClass()
            );
        }
        return "surface-readiness-timeout:"
                + diagnostic.stage().name().toLowerCase(Locale.ROOT);
    }

    private static String runtimeExceptionDetail(
            TacticalExceptionBoundary boundary,
            String exceptionClass
    ) {
        String prefix = "surface-readiness-timeout:runtime_exception:"
                + boundary.name().toLowerCase(Locale.ROOT) + ":";
        String token = sanitizedExceptionClass(exceptionClass, prefix);
        return prefix + token;
    }

    private static String sanitizedExceptionClass(
            String exceptionClass,
            String prefix
    ) {
        if (exceptionClass == null
                || exceptionClass.isEmpty()
                || !Normalizer.isNormalized(exceptionClass, Normalizer.Form.NFC)) {
            return "unknown_exception";
        }
        for (int index = 0; index < exceptionClass.length(); index++) {
            char value = exceptionClass.charAt(index);
            if (!(value >= 'A' && value <= 'Z')
                    && !(value >= 'a' && value <= 'z')
                    && !(value >= '0' && value <= '9')
                    && value != '.'
                    && value != '_'
                    && value != '$') {
                return "unknown_exception";
            }
        }
        int remaining = 1024
                - prefix.getBytes(StandardCharsets.UTF_8).length;
        return exceptionClass.length() <= remaining
                ? exceptionClass
                : exceptionClass.substring(0, remaining);
    }

    static String firstProjectorException(
            String current,
            RuntimeException failure
    ) {
        Objects.requireNonNull(failure, "failure");
        return current == null ? failure.getClass().getName() : current;
    }

    static void runTacticalTimeout(
            String detail,
            Consumer<String> logger,
            Runnable cleanup,
            Consumer<String> acknowledgement
    ) {
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(logger, "logger").accept(detail);
        runCleanupBestEffort(
                Objects.requireNonNull(cleanup, "cleanup"),
                () -> {
                    try {
                        Objects.requireNonNull(
                                acknowledgement, "acknowledgement"
                        ).accept(detail);
                    } catch (RuntimeException ignored) {
                        // The server deadline owns recovery after a failed ACK.
                    }
                }
        );
    }

    private static boolean projectEditor(Projection projection) {
        Optional<MapRoomDetail> rawDetail = FPSMClient.getGlobalData()
                .getMapRoomDetail();
        if (rawDetail.isEmpty()) {
            projection.editorDiagnostic = EditorReadinessStage.DETAIL_MISSING.token();
            return false;
        }
        MapRoomDetail detail = rawDetail.orElseThrow();
        if (!matchesDetail(projection.signal, detail)) {
            projection.editorDiagnostic = EditorReadinessStage.DETAIL_MISMATCH.token();
            return false;
        }
        if (captureRuntimeGeneration(projection) == null) {
            projection.editorDiagnostic =
                    EditorReadinessStage.RUNTIME_GENERATION_MISSING.token();
            return false;
        }
        if (projection.editor == null) {
            projection.editor = MinimapEditorScreens.openAcceptance(
                    detail,
                    failure -> projection.editorDiagnostic = failure.token()
            ).orElse(null);
            if (projection.editor == null) {
                if (projection.editorDiagnostic == null) {
                    projection.editorDiagnostic = EditorReadinessStage.OPEN_EMPTY.token();
                }
                return false;
            }
        }

        projection.editor.currentContext()
                .filter(context -> matchesEditorContext(projection.signal, context))
                .ifPresent(context -> projection.editorContext = context);
        if (!projection.editor.ownsScreen(Minecraft.getInstance().screen)) {
            projection.editorDiagnostic = EditorReadinessStage.SCREEN_NOT_OWNED.token();
            return false;
        }
        if (!projection.editor.isCurrent()) {
            projection.editorDiagnostic = EditorReadinessStage.GATEWAY_NOT_READY.token();
            return false;
        }

        switch (projection.signal.scene()) {
            case EDITOR_DIRTY -> {
                if (!projection.editStarted) {
                    projection.editStarted = projection.editor.applyAcceptanceEdit();
                }
                return projection.editStarted
                        && projection.editor.isDirty()
                        && projection.editor.status().filter(
                        status -> status == EditorStatus.DIRTY
                ).isPresent()
                        && projection.editorContext != null;
            }
            case EDITOR_PUBLISHING -> {
                return projectPublishingEditor(projection);
            }
            case EDITOR_ERROR -> {
                if (!projection.actionStarted) {
                    projection.actionStarted =
                            projection.editor.requestAcceptanceError();
                }
                return projection.actionStarted && editorErrorReady(projection);
            }
            default -> throw new IllegalStateException("not an editor scene");
        }
    }

    private static boolean projectPublishingEditor(Projection projection) {
        if (!projection.editStarted) {
            projection.editStarted = projection.editor.applyAcceptanceEdit();
            return false;
        }
        if (!projection.saveStarted) {
            boolean dirty = projection.editor.isDirty()
                    && projection.editor.status().filter(
                    status -> status == EditorStatus.DIRTY
            ).isPresent();
            if (!dirty) {
                return false;
            }
            projection.saveStarted = projection.editor.saveDraft();
            return false;
        }
        if (!projection.actionStarted) {
            boolean draftAcknowledged = !projection.editor.isDirty()
                    && projection.editor.status().filter(
                    status -> status == EditorStatus.READY
            ).isPresent();
            if (!draftAcknowledged) {
                return false;
            }
            projection.editor.currentContext()
                    .filter(context -> matchesEditorContext(projection.signal, context))
                    .ifPresent(context -> projection.editorContext = context);
            projection.actionStarted = projection.editor.publish();
        }
        return projection.actionStarted
                && projection.editor.status().filter(
                status -> status == EditorStatus.PUBLISHING
        ).isPresent()
                && projection.editorContext != null;
    }

    private static boolean editorErrorReady(Projection projection) {
        return projection.editor != null
                && projection.editor.lastError().isPresent()
                && projection.editorContext != null;
    }

    private static boolean projectMapRoom(Projection projection) {
        MapRoomDetail detail = FPSMClient.getGlobalData().getMapRoomDetail()
                .filter(candidate -> matchesDetail(projection.signal, candidate))
                .orElse(null);
        MapSelectionSnapshotS2CPacket snapshot =
                FPSMClient.getGlobalData().getMapSelectionSnapshot()
                        .filter(candidate -> candidate.passive()
                                && candidate.maps().stream().anyMatch(summary ->
                                matchesSummary(projection.signal, summary)))
                        .orElse(null);
        MapRoomToastS2CPacket toast = FPSMClient.getGlobalData().getMapRoomToast()
                .filter(MapRoomToastS2CPacket::error)
                .orElse(null);
        if (detail == null || snapshot == null || toast == null) {
            return false;
        }
        if (projection.mapRoom == null) {
            projection.mapRoom = FPSMMapSelectScreens.openAcceptance(
                    snapshot, detail, toast
            ).orElse(null);
        }
        return projection.mapRoom != null && projection.mapRoom.isCurrent();
    }

    private static RuntimeGeneration captureRuntimeGeneration(Projection projection) {
        if (projection.runtimeGeneration != null) {
            return FPSMClientPacketRegistrar.currentAcceptanceTacticalGeneration()
                    .filter(projection.runtimeGeneration::equals)
                    .orElse(null);
        }
        RuntimeGeneration generation =
                FPSMClientPacketRegistrar.currentAcceptanceTacticalGeneration()
                        .filter(candidate -> matchesRuntime(projection.signal, candidate))
                        .orElse(null);
        projection.runtimeGeneration = generation;
        return generation;
    }

    private static boolean matchesRuntime(
            BOMinimapAcceptanceSceneSignal signal,
            RuntimeGeneration generation
    ) {
        return signal.mapKey().equals(generation.mapKey())
                && signal.dimension().equals(generation.dimension())
                && signal.documentId().equals(generation.documentId())
                && signal.revision() == generation.revision()
                && signal.runtimeHash().equals(generation.runtimeHash());
    }

    private static boolean matchesDetail(
            BOMinimapAcceptanceSceneSignal signal,
            MapRoomDetail detail
    ) {
        return matchesSummary(signal, detail.summary())
                && detail.summary().currentPlayerOp();
    }

    private static boolean matchesSummary(
            BOMinimapAcceptanceSceneSignal signal,
            MapRoomSummary summary
    ) {
        if (!signal.mapKey().gameType().equals(summary.gameType())
                || !signal.mapKey().mapName().equals(summary.mapName())
                || !signal.dimension().toString().equals(summary.dimension())) {
            return false;
        }
        MapRoomMinimapIdentity identity = summary.minimapIdentity().orElse(null);
        return identity != null
                && signal.dimension().equals(identity.dimension())
                && signal.documentId().equals(identity.documentId())
                && signal.revision() == identity.revision()
                && signal.sourceHash().equals(identity.sourceHash())
                && signal.runtimeHash().equals(identity.runtimeHash());
    }

    private static boolean matchesEditorContext(
            BOMinimapAcceptanceSceneSignal signal,
            WireIdentity.EditorContext context
    ) {
        return context.lease().scope() == WireIdentity.Scope.EDITOR
                && signal.mapKey().equals(context.binding().target().mapKey())
                && signal.dimension().equals(context.binding().target().dimension())
                && signal.documentId().equals(context.binding().documentId())
                && signal.revision() == context.baseRevision()
                && signal.sourceHash().equals(context.baseSourceHash());
    }

    private static void finishApplied(Projection projection) {
        if (projection.acknowledged || STATE.get() != projection) {
            return;
        }
        if (isEditorScene(projection.signal.scene())) {
            if (projection.editorContext == null || projection.contextAttempted) {
                return;
            }
            projection.contextAttempted = true;
            if (!sendEditorContext(projection)) {
                return;
            }
        }
        projection.visible = true;
        projection.acknowledged = true;
        acknowledge(
                projection.signal,
                BOMinimapAcceptanceAck.Outcome.APPLIED,
                projection.signal.scene().commandName() + "-ready"
        );
    }

    private static boolean sendEditorContext(Projection projection) {
        try {
            BlockOffensive.INSTANCE.sendToServer(
                    new BOMinimapAcceptanceEditorContextC2SPacket(
                            new BOMinimapAcceptanceEditorContext(
                                    projection.signal.ownerId(),
                                    projection.signal.fixtureRunId(),
                                    projection.signal.epoch(),
                                    projection.editorContext
                            )
                    )
            );
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isEditorScene(BOMinimapAcceptanceSceneSignal.Scene scene) {
        return scene == BOMinimapAcceptanceSceneSignal.Scene.EDITOR_DIRTY
                || scene == BOMinimapAcceptanceSceneSignal.Scene.EDITOR_PUBLISHING
                || scene == BOMinimapAcceptanceSceneSignal.Scene.EDITOR_ERROR;
    }

    private static boolean ownsScreen(Projection projection, Screen screen) {
        return switch (projection.signal.scene()) {
            case TACTICAL -> projection.runtimeGeneration != null
                    && FPSMClientPacketRegistrar.ownsAcceptanceTacticalScreen(
                    projection.runtimeGeneration, screen
            );
            case EDITOR_DIRTY, EDITOR_PUBLISHING, EDITOR_ERROR ->
                    projection.editor != null && projection.editor.ownsScreen(screen);
            case MAP_ROOM_ERROR -> projection.mapRoom != null
                    && projection.mapRoom.ownsScreen(screen);
            case HUD_CROWDED -> false;
        };
    }

    private static void closeOwnedSurface(Projection projection) {
        switch (projection.signal.scene()) {
            case TACTICAL -> {
                if (projection.runtimeGeneration != null) {
                    FPSMClientPacketRegistrar.closeAcceptanceTactical(
                            projection.runtimeGeneration
                    );
                }
            }
            case EDITOR_DIRTY, EDITOR_PUBLISHING, EDITOR_ERROR -> {
                if (projection.editor != null) {
                    projection.editor.closeAcceptance();
                }
            }
            case MAP_ROOM_ERROR -> {
                if (projection.mapRoom != null) {
                    projection.mapRoom.closeAcceptance();
                }
            }
            case HUD_CROWDED -> {
                // HUD ownership is the correlated state only; no screen is closed.
            }
        }
    }

    static void runCleanupBestEffort(Runnable cleanup, Runnable terminal) {
        Objects.requireNonNull(cleanup, "cleanup");
        Objects.requireNonNull(terminal, "terminal");
        try {
            cleanup.run();
        } catch (RuntimeException ignored) {
            // Exact-owner cleanup must not strand the correlated terminal action.
        } finally {
            terminal.run();
        }
    }

    static boolean runIdempotentCleanup(
            AtomicBoolean guard,
            Runnable cleanup,
            Runnable terminal
    ) {
        Objects.requireNonNull(guard, "guard");
        if (!guard.compareAndSet(false, true)) {
            return false;
        }
        runCleanupBestEffort(cleanup, terminal);
        return true;
    }

    private static void acknowledge(
            BOMinimapAcceptanceSceneSignal signal,
            BOMinimapAcceptanceAck.Outcome outcome,
            String detail
    ) {
        try {
            BlockOffensive.INSTANCE.sendToServer(new BOMinimapAcceptanceAckC2SPacket(
                    new BOMinimapAcceptanceAck(
                            signal.ownerId(), signal.fixtureRunId(), signal.epoch(),
                            outcome, detail
                    )
            ));
        } catch (RuntimeException ignored) {
            // The server deadline owns recovery after a disconnect or channel shutdown.
        }
    }

    private static final class Projection {
        private final long connectionGeneration;
        private final BOMinimapAcceptanceSceneSignal signal;
        private int pendingTicks;
        private boolean surfaceOpened;
        private boolean editStarted;
        private boolean saveStarted;
        private boolean actionStarted;
        private boolean contextAttempted;
        private boolean acknowledged;
        private final AtomicBoolean teardownStarted = new AtomicBoolean();
        private boolean visible;
        private RuntimeGeneration runtimeGeneration;
        private TacticalDiagnosticSnapshot tacticalDiagnostic;
        private String projectorException;
        private String editorDiagnostic;
        private WireIdentity.EditorContext editorContext;
        private MinimapEditorScreens.AcceptanceHandle editor;
        private FPSMMapSelectScreens.AcceptanceHandle mapRoom;

        private Projection(
                long connectionGeneration,
                BOMinimapAcceptanceSceneSignal signal
        ) {
            this.connectionGeneration = connectionGeneration;
            this.signal = Objects.requireNonNull(signal, "signal");
        }

        private boolean matches(UUID ownerId, UUID fixtureRunId, long epoch) {
            return signal.ownerId().equals(ownerId)
                    && signal.fixtureRunId().equals(fixtureRunId)
                    && signal.epoch() == epoch;
        }
    }

    private enum EditorReadinessStage {
        DETAIL_MISSING,
        DETAIL_MISMATCH,
        RUNTIME_GENERATION_MISSING,
        SCREEN_NOT_OWNED,
        GATEWAY_NOT_READY,
        OPEN_EMPTY;

        private String token() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record State(
            long connectionGeneration,
            UUID ownerId,
            UUID fixtureRunId,
            long epoch,
            BOMinimapAcceptanceSceneSignal signal,
            boolean visible
    ) {
    }
}
