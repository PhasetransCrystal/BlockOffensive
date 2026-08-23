package com.ptcrys.blockoffensive.minimap;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Translates authoritative server events into intel ledger mutations.
 * World raycasts stay in a platform adapter; this class stays pure over poses/ticks.
 */
public final class CSMinimapVisibilityEventBridge {
    private final CSMinimapIntelLedger ledger;

    public CSMinimapVisibilityEventBridge(CSMinimapIntelLedger ledger) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
    }

    public void onLineOfSight(String viewerTeamId, UUID targetId, long nowTick, double x, double y, double z, float yaw, Optional<String> floorSlug) {
        ledger.noteLos(viewerTeamId, targetId, nowTick, x, y, z, yaw, floorSlug);
    }

    public void onFireExposure(String viewerTeamId, UUID targetId, long nowTick, double x, double y, double z, float yaw, Optional<String> floorSlug) {
        ledger.noteFire(viewerTeamId, targetId, nowTick, x, y, z, yaw, floorSlug);
    }

    public void onTargetDeathOrLeave(UUID targetId) {
        ledger.removeTarget(targetId);
    }

    public void onRoundOrMapReset() {
        ledger.clearAll();
    }

    public void onTeamReset(String teamId) {
        ledger.clearTeam(teamId);
    }

    public void tick(long nowTick) {
        ledger.tick(nowTick);
    }
}