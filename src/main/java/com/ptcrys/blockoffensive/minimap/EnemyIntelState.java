package com.ptcrys.blockoffensive.minimap;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared team intel about an enemy player. Pure server-side state.
 */
public final class EnemyIntelState {
    public enum Mode {
        ACTIVE,
        LAST_KNOWN
    }

    private final UUID targetId;
    private Mode mode;
    private long activeUntilTick;
    private long lastKnownUntilTick;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private long updatedTick;
    private Optional<String> floorSlug;

    public EnemyIntelState(UUID targetId) {
        this.targetId = Objects.requireNonNull(targetId, "targetId");
        this.mode = Mode.ACTIVE;
        this.floorSlug = Optional.empty();
    }

    public UUID targetId() {
        return targetId;
    }

    public Mode mode() {
        return mode;
    }

    public void noteLosOrFire(long nowTick, long durationTicks, double x, double y, double z, float yaw, Optional<String> floorSlug) {
        this.mode = Mode.ACTIVE;
        this.activeUntilTick = Math.max(this.activeUntilTick, nowTick + durationTicks);
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.updatedTick = nowTick;
        this.floorSlug = Objects.requireNonNull(floorSlug, "floorSlug");
        this.lastKnownUntilTick = 0L;
    }

    /**
     * Advance shared intel. Active is inclusive through activeUntilTick.
     * On the first tick after that, freezes into LAST_KNOWN for lastKnownTicks.
     * @return false if fully expired and should be removed
     */
    public boolean tick(long nowTick, long lastKnownTicks) {
        if (mode == Mode.ACTIVE) {
            if (nowTick <= activeUntilTick) {
                return true;
            }
            // just expired active
            if (lastKnownTicks <= 0) {
                return false;
            }
            mode = Mode.LAST_KNOWN;
            // last known window starts after active expiry
            lastKnownUntilTick = activeUntilTick + lastKnownTicks;
            return nowTick <= lastKnownUntilTick;
        }
        return nowTick <= lastKnownUntilTick;
    }

    public void clear() {
        mode = Mode.LAST_KNOWN;
        activeUntilTick = 0L;
        lastKnownUntilTick = 0L;
    }

    public boolean isActive() {
        return mode == Mode.ACTIVE;
    }

    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public long updatedTick() { return updatedTick; }
    public Optional<String> floorSlug() { return floorSlug; }
    public long activeUntilTick() { return activeUntilTick; }
    public long lastKnownUntilTick() { return lastKnownUntilTick; }
}