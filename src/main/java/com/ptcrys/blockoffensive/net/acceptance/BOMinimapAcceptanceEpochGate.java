package com.ptcrys.blockoffensive.net.acceptance;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Connection-scoped replay/gap gate for acceptance scene commands. */
public final class BOMinimapAcceptanceEpochGate {
    public static final long MAX_EPOCH = Long.MAX_VALUE - 1L;
    private static final int DEFAULT_RETIRED_RUN_CAPACITY = 64;

    private final int retiredRunCapacity;
    private final Set<RunKey> retiredRuns = new LinkedHashSet<>();
    private long connectionGeneration = 1L;
    private Tuple committed;
    private Tuple manuallyClosed;
    private Tuple cleared;
    private boolean retirementSaturated;

    public BOMinimapAcceptanceEpochGate() {
        this(DEFAULT_RETIRED_RUN_CAPACITY);
    }

    BOMinimapAcceptanceEpochGate(int retiredRunCapacity) {
        if (retiredRunCapacity <= 0) {
            throw new IllegalArgumentException("retired run capacity must be positive");
        }
        this.retiredRunCapacity = retiredRunCapacity;
    }

    /** Compatibility entry point: legacy callers always submit SHOW actions. */
    public synchronized Decision accept(UUID ownerId, UUID fixtureRunId, long epoch) {
        return accept(BOMinimapAcceptanceSceneSignal.Action.SHOW,
                ownerId, fixtureRunId, epoch);
    }

    public synchronized Decision accept(
            BOMinimapAcceptanceSceneSignal.Action action,
            UUID ownerId,
            UUID fixtureRunId,
            long epoch
    ) {
        Objects.requireNonNull(action, "action");
        Tuple candidate = tuple(ownerId, fixtureRunId, epoch);
        return action == BOMinimapAcceptanceSceneSignal.Action.CLEAR
                ? acceptClear(candidate)
                : acceptShow(candidate);
    }

    private Decision acceptShow(Tuple candidate) {
        // CLEAR is terminal for the whole run even when the retired-run set is full.
        if (candidate.equals(manuallyClosed)
                || (cleared != null && cleared.sameRun(candidate))) {
            return Decision.IGNORE_CLOSED;
        }
        RunKey run = candidate.runKey();
        if (retiredRuns.contains(run)) {
            return Decision.IGNORE_RETIRED;
        }
        if (committed == null) {
            if (candidate.epoch() != 1L) {
                return Decision.IGNORE_GAP;
            }
            committed = candidate;
            return Decision.APPLY;
        }
        if (!committed.sameRun(candidate)) {
            if (candidate.epoch() != 1L) {
                return Decision.IGNORE_GAP;
            }
            if (!retireForSwitch(committed.runKey())) {
                return Decision.IGNORE_CAPACITY;
            }
            committed = candidate;
            manuallyClosed = null;
            cleared = null;
            return Decision.APPLY;
        }
        if (candidate.epoch() <= committed.epoch()) {
            return Decision.IGNORE_STALE;
        }
        if (!isNext(committed.epoch(), candidate.epoch())) {
            return Decision.IGNORE_GAP;
        }
        committed = candidate;
        if (manuallyClosed != null && manuallyClosed.sameRun(candidate)
                && manuallyClosed.epoch() < candidate.epoch()) {
            manuallyClosed = null;
        }
        return Decision.APPLY;
    }

    private Decision acceptClear(Tuple candidate) {
        if (candidate.equals(cleared)) {
            return Decision.IGNORE_CLOSED;
        }
        RunKey run = candidate.runKey();
        if (retiredRuns.contains(run)) {
            return Decision.IGNORE_RETIRED;
        }
        if (committed == null) {
            if (candidate.epoch() != 1L) {
                return Decision.IGNORE_GAP;
            }
        } else if (!committed.sameRun(candidate)) {
            return Decision.IGNORE_STALE;
        } else if (candidate.epoch() < committed.epoch()) {
            return Decision.IGNORE_STALE;
        } else if (candidate.epoch() != committed.epoch()
                && !isNext(committed.epoch(), candidate.epoch())) {
            return Decision.IGNORE_GAP;
        }

        committed = candidate;
        cleared = candidate;
        manuallyClosed = null;
        retireAfterClear(run);
        return Decision.APPLY;
    }

    private boolean retireForSwitch(RunKey run) {
        if (retiredRuns.contains(run)) {
            return true;
        }
        if (retirementSaturated || retiredRuns.size() >= retiredRunCapacity) {
            retirementSaturated = true;
            return false;
        }
        retiredRuns.add(run);
        return true;
    }

    private void retireAfterClear(RunKey run) {
        if (retiredRuns.contains(run)) {
            return;
        }
        if (retiredRuns.size() >= retiredRunCapacity) {
            retirementSaturated = true;
            return;
        }
        retiredRuns.add(run);
    }

    public synchronized void markClosed(UUID ownerId, UUID fixtureRunId, long epoch) {
        Tuple candidate = tuple(ownerId, fixtureRunId, epoch);
        if (committed != null && committed.equals(candidate)
                && !candidate.equals(cleared)) {
            manuallyClosed = candidate;
        }
    }

    public synchronized void resetConnection() {
        connectionGeneration = connectionGeneration >= MAX_EPOCH
                ? 1L
                : connectionGeneration + 1L;
        committed = null;
        manuallyClosed = null;
        cleared = null;
        retiredRuns.clear();
        retirementSaturated = false;
    }

    public synchronized long connectionGeneration() {
        return connectionGeneration;
    }

    private Tuple tuple(UUID ownerId, UUID fixtureRunId, long epoch) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(fixtureRunId, "fixtureRunId");
        if (epoch <= 0 || epoch > MAX_EPOCH) {
            throw new IllegalArgumentException("epoch is outside the supported range");
        }
        return new Tuple(connectionGeneration, ownerId, fixtureRunId, epoch);
    }

    private static boolean isNext(long previous, long candidate) {
        return previous < MAX_EPOCH && candidate == previous + 1L;
    }

    public enum Decision {
        APPLY,
        IGNORE_STALE,
        IGNORE_GAP,
        IGNORE_CLOSED,
        IGNORE_RETIRED,
        IGNORE_CAPACITY
    }

    private record RunKey(long connectionGeneration, UUID ownerId, UUID fixtureRunId) {
    }

    private record Tuple(
            long connectionGeneration,
            UUID ownerId,
            UUID fixtureRunId,
            long epoch
    ) {
        private boolean sameRun(Tuple other) {
            return runKey().equals(other.runKey());
        }

        private RunKey runKey() {
            return new RunKey(connectionGeneration, ownerId, fixtureRunId);
        }
    }
}
