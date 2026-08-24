package com.ptcrys.blockoffensive.net.acceptance;

import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/** Server-side correlation ledger for one non-production fixture run. */
public final class BOMinimapAcceptanceServerLedger {
    private final UUID ownerId;
    private final UUID fixtureRunId;
    private final Predicate<WireIdentity.EditorContext> editorContextAuthority;
    private long expectedEpoch;
    private long deadlineTick;
    private ScenePolicy scenePolicy = ScenePolicy.STANDARD;
    private Optional<WireIdentity.EditorContext> expectedEditorContext = Optional.empty();
    private BOMinimapAcceptanceAck acknowledgement;
    private boolean editorContextAccepted;
    private Status status = Status.IDLE;

    public BOMinimapAcceptanceServerLedger(UUID ownerId, UUID fixtureRunId) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.fixtureRunId = Objects.requireNonNull(fixtureRunId, "fixtureRunId");
        this.editorContextAuthority = null;
    }

    public BOMinimapAcceptanceServerLedger(
            UUID ownerId,
            UUID fixtureRunId,
            Predicate<WireIdentity.EditorContext> editorContextAuthority
    ) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.fixtureRunId = Objects.requireNonNull(fixtureRunId, "fixtureRunId");
        this.editorContextAuthority = Objects.requireNonNull(
                editorContextAuthority, "editorContextAuthority"
        );
    }

    /** Compatibility entry point for the original loader-neutral tests. */
    public synchronized void expect(
            long epoch,
            Optional<WireIdentity.EditorContext> editorContext
    ) {
        Optional<WireIdentity.EditorContext> expected = Objects.requireNonNull(
                editorContext, "editorContext"
        );
        expect(epoch,
                expected.isPresent()
                        ? ScenePolicy.EDITOR_REQUIRES_CONTEXT
                        : ScenePolicy.STANDARD,
                expected,
                Long.MAX_VALUE);
        markDispatched(epoch);
    }

    public synchronized void expect(
            long epoch,
            ScenePolicy policy,
            Optional<WireIdentity.EditorContext> editorContext,
            long deadlineTick
    ) {
        requireEpoch(epoch);
        ScenePolicy nextPolicy = Objects.requireNonNull(policy, "policy");
        Optional<WireIdentity.EditorContext> nextContext = Objects.requireNonNull(
                editorContext, "editorContext"
        );
        if (deadlineTick <= 0) {
            throw new IllegalArgumentException("deadline tick must be positive");
        }
        if (nextPolicy != ScenePolicy.EDITOR_REQUIRES_CONTEXT
                && nextContext.isPresent()) {
            throw new IllegalArgumentException(
                    "only an editor scene may declare an expected editor context"
            );
        }
        if (nextPolicy == ScenePolicy.EDITOR_REQUIRES_CONTEXT
                && editorContextAuthority == null) {
            throw new IllegalStateException(
                    "editor context expectations require an authority predicate"
            );
        }
        if (status == Status.SEND_UNKNOWN) {
            throw new IllegalStateException(
                    "unknown scene delivery poisons this fixture run until cleanup"
            );
        }
        if (isWaiting(status) || status == Status.RESERVED) {
            throw new IllegalStateException("a scene expectation is already active");
        }
        boolean abortedRetry = status == Status.ABORTED && epoch == expectedEpoch;
        if (!abortedRetry && expectedEpoch != 0L && epoch <= expectedEpoch) {
            throw new IllegalStateException(
                    "expected epoch must advance unless the prior reservation aborted"
            );
        }

        expectedEpoch = epoch;
        this.deadlineTick = deadlineTick;
        scenePolicy = nextPolicy;
        expectedEditorContext = nextContext;
        acknowledgement = null;
        editorContextAccepted = false;
        status = Status.RESERVED;
    }

    public synchronized void abort(long epoch) {
        requireCurrent(epoch);
        if (status != Status.RESERVED) {
            throw new IllegalStateException("only an unattempted reservation can abort");
        }
        status = Status.ABORTED;
    }

    public synchronized void markDispatched(long epoch) {
        requireCurrent(epoch);
        if (status != Status.RESERVED) {
            throw new IllegalStateException("only a reserved scene can be dispatched");
        }
        status = Status.DISPATCHED;
    }

    public synchronized void markSendUnknown(long epoch) {
        requireCurrent(epoch);
        if (status != Status.RESERVED && status != Status.DISPATCHED) {
            if (status == Status.SEND_UNKNOWN) {
                return;
            }
            throw new IllegalStateException(
                    "send uncertainty can only follow a scene send attempt"
            );
        }
        status = Status.SEND_UNKNOWN;
    }

    public synchronized void tick(long nowTick) {
        if ((status == Status.RESERVED || isWaiting(status))
                && nowTick >= deadlineTick) {
            status = Status.TIMED_OUT;
        }
    }

    public synchronized boolean acceptAck(
            UUID senderId,
            BOMinimapAcceptanceAck candidate
    ) {
        if (candidate == null || !canReceive()
                || !matches(senderId, candidate.ownerId(),
                candidate.fixtureRunId(), candidate.epoch())
                || acknowledgement != null) {
            return false;
        }
        if (scenePolicy == ScenePolicy.EDITOR_UNSUPPORTED
                && candidate.outcome() == BOMinimapAcceptanceAck.Outcome.APPLIED) {
            acknowledgement = candidate;
            status = Status.PROTOCOL_FAILED;
            return false;
        }

        acknowledgement = candidate;
        switch (candidate.outcome()) {
            case APPLIED -> status = scenePolicy == ScenePolicy.EDITOR_REQUIRES_CONTEXT
                    && !editorContextAccepted
                    ? Status.WAITING_CONTEXT
                    : Status.SUCCEEDED;
            case TIMED_OUT -> status = Status.TIMED_OUT;
            case FAILED, IGNORED, CLOSED -> status = Status.FAILED;
        }
        return true;
    }

    public synchronized boolean acceptEditorContext(
            UUID senderId,
            BOMinimapAcceptanceEditorContext candidate
    ) {
        if (candidate == null || !canReceive()
                || !matches(senderId, candidate.ownerId(),
                candidate.fixtureRunId(), candidate.epoch())
                || editorContextAccepted) {
            return false;
        }
        if (scenePolicy != ScenePolicy.EDITOR_REQUIRES_CONTEXT) {
            status = Status.PROTOCOL_FAILED;
            return false;
        }
        if (expectedEditorContext.isPresent()
                && !expectedEditorContext.orElseThrow().equals(candidate.context())) {
            return false;
        }
        try {
            if (!editorContextAuthority.test(candidate.context())) {
                return false;
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        editorContextAccepted = true;
        status = acknowledgement != null
                && acknowledgement.outcome() == BOMinimapAcceptanceAck.Outcome.APPLIED
                ? Status.SUCCEEDED
                : Status.WAITING_ACK;
        return true;
    }

    public synchronized Optional<BOMinimapAcceptanceAck> acknowledgement() {
        return Optional.ofNullable(acknowledgement);
    }

    public synchronized boolean editorContextAccepted() {
        return editorContextAccepted;
    }

    public synchronized Status status() {
        return status;
    }

    public synchronized long expectedEpoch() {
        return expectedEpoch;
    }

    private boolean canReceive() {
        return status == Status.DISPATCHED
                || status == Status.WAITING_ACK
                || status == Status.WAITING_CONTEXT;
    }

    private static boolean isWaiting(Status candidate) {
        return candidate == Status.DISPATCHED
                || candidate == Status.WAITING_ACK
                || candidate == Status.WAITING_CONTEXT;
    }

    private void requireCurrent(long epoch) {
        requireEpoch(epoch);
        if (expectedEpoch != epoch) {
            throw new IllegalStateException("scene epoch does not match the active reservation");
        }
    }

    private static void requireEpoch(long epoch) {
        if (epoch <= 0 || epoch > BOMinimapAcceptanceEpochGate.MAX_EPOCH) {
            throw new IllegalArgumentException("epoch is outside the supported range");
        }
    }

    private boolean matches(UUID senderId, UUID owner, UUID run, long epoch) {
        return ownerId.equals(senderId)
                && ownerId.equals(owner)
                && fixtureRunId.equals(run)
                && expectedEpoch == epoch;
    }

    public enum ScenePolicy {
        STANDARD,
        EDITOR_REQUIRES_CONTEXT,
        EDITOR_UNSUPPORTED
    }

    public enum Status {
        IDLE,
        RESERVED,
        DISPATCHED,
        WAITING_ACK,
        WAITING_CONTEXT,
        SUCCEEDED,
        ABORTED,
        FAILED,
        SEND_UNKNOWN,
        TIMED_OUT,
        PROTOCOL_FAILED
    }
}
