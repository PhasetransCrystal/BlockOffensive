package com.ptcrys.blockoffensive.minimap;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Event-driven authoritative C4 objective tracker. Pure; no Minecraft entity types.
 * Transitions must be driven by server lifecycle events, not outline/HUD scans.
 */
public final class CSGameObjectiveTracker {
    public static final long DEFAULT_DROPPED_CT_LAST_KNOWN_TICKS = 60L;

    private final long droppedCtLastKnownTicks;
    private C4ObjectiveSnapshot snapshot = C4ObjectiveSnapshot.none();

    public CSGameObjectiveTracker() {
        this(DEFAULT_DROPPED_CT_LAST_KNOWN_TICKS);
    }

    public CSGameObjectiveTracker(long droppedCtLastKnownTicks) {
        if (droppedCtLastKnownTicks < 0) {
            throw new IllegalArgumentException("droppedCtLastKnownTicks must be non-negative");
        }
        this.droppedCtLastKnownTicks = droppedCtLastKnownTicks;
    }

    public C4ObjectiveSnapshot snapshot() {
        return snapshot;
    }

    public void assignCarrier(UUID carrierId, long tick, double x, double y, double z, float yaw, Optional<String> floorSlug) {
        Objects.requireNonNull(carrierId, "carrierId");
        floorSlug = Objects.requireNonNull(floorSlug, "floorSlug");
        snapshot = new C4ObjectiveSnapshot(
                C4ObjectivePhase.CARRIED,
                Optional.of(carrierId),
                OptionalInt.empty(),
                Optional.empty(),
                x, y, z, yaw,
                tick,
                floorSlug,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false,
                Optional.empty()
        );
    }

    public void noteCarrierPose(UUID carrierId, long tick, double x, double y, double z, float yaw, Optional<String> floorSlug) {
        Objects.requireNonNull(carrierId, "carrierId");
        floorSlug = Objects.requireNonNull(floorSlug, "floorSlug");
        if (snapshot.phase() != C4ObjectivePhase.CARRIED) {
            return;
        }
        if (snapshot.carrierId().isEmpty() || !snapshot.carrierId().get().equals(carrierId)) {
            return;
        }
        snapshot = new C4ObjectiveSnapshot(
                C4ObjectivePhase.CARRIED,
                Optional.of(carrierId),
                OptionalInt.empty(),
                Optional.empty(),
                x, y, z, yaw,
                tick,
                floorSlug,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false,
                Optional.empty()
        );
    }

    public void forceDrop(int entityId, UUID entityUuid, long tick, double x, double y, double z, float yaw, Optional<String> floorSlug) {
        onDropped(entityId, entityUuid, tick, x, y, z, yaw, floorSlug, false);
    }

    public void manualDrop(int entityId, UUID entityUuid, long tick, double x, double y, double z, float yaw, Optional<String> floorSlug) {
        onDropped(entityId, entityUuid, tick, x, y, z, yaw, floorSlug, false);
    }

    private void onDropped(
            int entityId,
            UUID entityUuid,
            long tick,
            double x,
            double y,
            double z,
            float yaw,
            Optional<String> floorSlug,
            boolean knownToCt
    ) {
        Objects.requireNonNull(entityUuid, "entityUuid");
        floorSlug = Objects.requireNonNull(floorSlug, "floorSlug");
        if (entityId < 0) {
            throw new IllegalArgumentException("entityId must be non-negative");
        }
        snapshot = new C4ObjectiveSnapshot(
                C4ObjectivePhase.DROPPED,
                Optional.empty(),
                OptionalInt.of(entityId),
                Optional.of(entityUuid),
                x, y, z, yaw,
                tick,
                floorSlug,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                knownToCt,
                knownToCt && droppedCtLastKnownTicks > 0
                        ? Optional.of(tick + droppedCtLastKnownTicks)
                        : Optional.empty()
        );
    }

    public void noteDroppedPose(int entityId, long tick, double x, double y, double z, float yaw, Optional<String> floorSlug) {
        floorSlug = Objects.requireNonNull(floorSlug, "floorSlug");
        if (snapshot.phase() != C4ObjectivePhase.DROPPED) {
            return;
        }
        if (snapshot.entityId().isEmpty() || snapshot.entityId().getAsInt() != entityId) {
            return;
        }
        boolean known = snapshot.droppedKnownToCt();
        Optional<Long> lastKnown = snapshot.droppedCtLastKnownUntil();
        snapshot = new C4ObjectiveSnapshot(
                C4ObjectivePhase.DROPPED,
                Optional.empty(),
                OptionalInt.of(entityId),
                snapshot.entityUuid(),
                x, y, z, yaw,
                tick,
                floorSlug,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                known,
                lastKnown
        );
    }

    /**
     * Surviving CT LOS discovery of dropped C4. Shared by the CT team; dead CT do not call this.
     */
    public void noteDroppedDiscoveredByCt(long tick, double x, double y, double z, float yaw, Optional<String> floorSlug) {
        floorSlug = Objects.requireNonNull(floorSlug, "floorSlug");
        if (snapshot.phase() != C4ObjectivePhase.DROPPED) {
            return;
        }
        Optional<Long> until = droppedCtLastKnownTicks > 0
                ? Optional.of(tick + droppedCtLastKnownTicks)
                : Optional.empty();
        snapshot = new C4ObjectiveSnapshot(
                C4ObjectivePhase.DROPPED,
                Optional.empty(),
                snapshot.entityId(),
                snapshot.entityUuid(),
                x, y, z, yaw,
                tick,
                floorSlug,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                true,
                until
        );
    }

    public void successfulPickup(UUID carrierId, long tick, double x, double y, double z, float yaw, Optional<String> floorSlug) {
        assignCarrier(carrierId, tick, x, y, z, yaw, floorSlug);
    }

    public void planted(
            int entityId,
            UUID entityUuid,
            long tick,
            double x,
            double y,
            double z,
            float yaw,
            Optional<String> floorSlug,
            Optional<String> siteId
    ) {
        Objects.requireNonNull(entityUuid, "entityUuid");
        floorSlug = Objects.requireNonNull(floorSlug, "floorSlug");
        siteId = Objects.requireNonNull(siteId, "siteId");
        if (entityId < 0) {
            throw new IllegalArgumentException("entityId must be non-negative");
        }
        snapshot = new C4ObjectiveSnapshot(
                C4ObjectivePhase.PLANTED,
                Optional.empty(),
                OptionalInt.of(entityId),
                Optional.of(entityUuid),
                x, y, z, yaw,
                tick,
                floorSlug,
                siteId,
                Optional.empty(),
                Optional.empty(),
                false,
                Optional.empty()
        );
    }

    public void notePlantedPose(int entityId, long tick, double x, double y, double z, float yaw, Optional<String> floorSlug) {
        floorSlug = Objects.requireNonNull(floorSlug, "floorSlug");
        if (snapshot.phase() != C4ObjectivePhase.PLANTED && snapshot.phase() != C4ObjectivePhase.DEFUSING) {
            return;
        }
        if (snapshot.entityId().isEmpty() || snapshot.entityId().getAsInt() != entityId) {
            return;
        }
        C4ObjectivePhase phase = snapshot.phase();
        snapshot = new C4ObjectiveSnapshot(
                phase,
                Optional.empty(),
                OptionalInt.of(entityId),
                snapshot.entityUuid(),
                x, y, z, yaw,
                tick,
                floorSlug,
                snapshot.siteId(),
                snapshot.defuserId(),
                snapshot.defuseProgress(),
                false,
                Optional.empty()
        );
    }

    public void startDefusing(UUID defuserId, long tick, float progress) {
        Objects.requireNonNull(defuserId, "defuserId");
        if (snapshot.phase() != C4ObjectivePhase.PLANTED && snapshot.phase() != C4ObjectivePhase.DEFUSING) {
            return;
        }
        if (!Float.isFinite(progress) || progress < 0f || progress > 1f) {
            throw new IllegalArgumentException("progress must be in [0,1]");
        }
        snapshot = new C4ObjectiveSnapshot(
                C4ObjectivePhase.DEFUSING,
                Optional.empty(),
                snapshot.entityId(),
                snapshot.entityUuid(),
                snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(),
                tick,
                snapshot.floorSlug(),
                snapshot.siteId(),
                Optional.of(defuserId),
                Optional.of(progress),
                false,
                Optional.empty()
        );
    }

    public void updateDefuseProgress(UUID defuserId, long tick, float progress) {
        Objects.requireNonNull(defuserId, "defuserId");
        if (snapshot.phase() != C4ObjectivePhase.DEFUSING) {
            return;
        }
        if (snapshot.defuserId().isEmpty() || !snapshot.defuserId().get().equals(defuserId)) {
            return;
        }
        if (!Float.isFinite(progress) || progress < 0f || progress > 1f) {
            throw new IllegalArgumentException("progress must be in [0,1]");
        }
        snapshot = new C4ObjectiveSnapshot(
                C4ObjectivePhase.DEFUSING,
                Optional.empty(),
                snapshot.entityId(),
                snapshot.entityUuid(),
                snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(),
                tick,
                snapshot.floorSlug(),
                snapshot.siteId(),
                Optional.of(defuserId),
                Optional.of(progress),
                false,
                Optional.empty()
        );
    }

    public void cancelDefuse(long tick) {
        if (snapshot.phase() != C4ObjectivePhase.DEFUSING) {
            return;
        }
        snapshot = new C4ObjectiveSnapshot(
                C4ObjectivePhase.PLANTED,
                Optional.empty(),
                snapshot.entityId(),
                snapshot.entityUuid(),
                snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(),
                tick,
                snapshot.floorSlug(),
                snapshot.siteId(),
                Optional.empty(),
                Optional.empty(),
                false,
                Optional.empty()
        );
    }

    public void defused(long tick) {
        if (!snapshot.isActiveObjective() && snapshot.phase() != C4ObjectivePhase.DEFUSING) {
            // still allow terminal if planted-related
        }
        snapshot = new C4ObjectiveSnapshot(
                C4ObjectivePhase.DEFUSED,
                Optional.empty(),
                snapshot.entityId(),
                snapshot.entityUuid(),
                snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(),
                tick,
                snapshot.floorSlug(),
                snapshot.siteId(),
                Optional.empty(),
                Optional.empty(),
                false,
                Optional.empty()
        );
    }

    public void exploded(long tick) {
        snapshot = new C4ObjectiveSnapshot(
                C4ObjectivePhase.EXPLODED,
                Optional.empty(),
                snapshot.entityId(),
                snapshot.entityUuid(),
                snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(),
                tick,
                snapshot.floorSlug(),
                snapshot.siteId(),
                Optional.empty(),
                Optional.empty(),
                false,
                Optional.empty()
        );
    }

    public void removed(long tick) {
        snapshot = new C4ObjectiveSnapshot(
                C4ObjectivePhase.REMOVED,
                Optional.empty(),
                OptionalInt.empty(),
                Optional.empty(),
                snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(),
                tick,
                snapshot.floorSlug(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false,
                Optional.empty()
        );
    }

    public void roundReset() {
        snapshot = C4ObjectiveSnapshot.none();
    }

    public void carrierDisconnectedOrDied(int dropEntityId, UUID dropEntityUuid, long tick, double x, double y, double z, float yaw, Optional<String> floorSlug) {
        if (snapshot.phase() != C4ObjectivePhase.CARRIED) {
            return;
        }
        forceDrop(dropEntityId, dropEntityUuid, tick, x, y, z, yaw, floorSlug);
    }

    /**
     * Advance dropped CT last-known window. Does not invent discovery.
     */
    public void tick(long nowTick) {
        if (snapshot.phase() != C4ObjectivePhase.DROPPED) {
            return;
        }
        if (!snapshot.droppedKnownToCt()) {
            return;
        }
        Optional<Long> until = snapshot.droppedCtLastKnownUntil();
        if (until.isPresent() && nowTick > until.get()) {
            // discovery window expired: CT no longer knows live drop location until rediscovered
            snapshot = new C4ObjectiveSnapshot(
                    C4ObjectivePhase.DROPPED,
                    Optional.empty(),
                    snapshot.entityId(),
                    snapshot.entityUuid(),
                    snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(),
                    snapshot.updatedTick(),
                    snapshot.floorSlug(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    false,
                    Optional.empty()
            );
        }
    }

    public boolean isDroppedEntity(int entityId) {
        return snapshot.phase() == C4ObjectivePhase.DROPPED
                && snapshot.entityId().isPresent()
                && snapshot.entityId().getAsInt() == entityId;
    }

    public boolean isPlantedEntity(int entityId) {
        return (snapshot.phase() == C4ObjectivePhase.PLANTED || snapshot.phase() == C4ObjectivePhase.DEFUSING)
                && snapshot.entityId().isPresent()
                && snapshot.entityId().getAsInt() == entityId;
    }

    public OptionalInt stableEntityId() {
        return snapshot.entityId();
    }

    public Optional<UUID> stableEntityUuid() {
        return snapshot.entityUuid();
    }
}