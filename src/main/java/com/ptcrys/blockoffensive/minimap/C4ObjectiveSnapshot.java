package com.ptcrys.blockoffensive.minimap;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Pure authoritative C4 objective snapshot. No Minecraft types.
 */
public record C4ObjectiveSnapshot(
        C4ObjectivePhase phase,
        Optional<UUID> carrierId,
        OptionalInt entityId,
        Optional<UUID> entityUuid,
        double x,
        double y,
        double z,
        float yaw,
        long updatedTick,
        Optional<String> floorSlug,
        Optional<String> siteId,
        Optional<UUID> defuserId,
        Optional<Float> defuseProgress,
        boolean droppedKnownToCt,
        Optional<Long> droppedCtLastKnownUntil
) {
    public C4ObjectiveSnapshot {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(carrierId, "carrierId");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(entityUuid, "entityUuid");
        Objects.requireNonNull(floorSlug, "floorSlug");
        Objects.requireNonNull(siteId, "siteId");
        Objects.requireNonNull(defuserId, "defuserId");
        Objects.requireNonNull(defuseProgress, "defuseProgress");
        Objects.requireNonNull(droppedCtLastKnownUntil, "droppedCtLastKnownUntil");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw)) {
            throw new IllegalArgumentException("C4 pose must be finite");
        }
        if (updatedTick < 0) {
            throw new IllegalArgumentException("updatedTick must be non-negative");
        }
        defuseProgress.ifPresent(p -> {
            if (!Float.isFinite(p) || p < 0f || p > 1f) {
                throw new IllegalArgumentException("defuseProgress must be in [0,1]");
            }
        });
    }

    public boolean isActiveObjective() {
        return phase == C4ObjectivePhase.CARRIED
                || phase == C4ObjectivePhase.DROPPED
                || phase == C4ObjectivePhase.PLANTED
                || phase == C4ObjectivePhase.DEFUSING;
    }

    public static C4ObjectiveSnapshot none() {
        return new C4ObjectiveSnapshot(
                C4ObjectivePhase.NONE,
                Optional.empty(),
                OptionalInt.empty(),
                Optional.empty(),
                0, 0, 0, 0f,
                0L,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false,
                Optional.empty()
        );
    }
}