package com.ptcrys.blockoffensive.minimap;

import com.ptcrys.fpsmatch.core.minimap.marker.DeathMarkerEvent;
import com.ptcrys.fpsmatch.core.minimap.marker.DeathMarkerLedger;
import com.ptcrys.fpsmatch.core.minimap.marker.PlayerPoseSnapshot;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Captures authoritative death poses at DeathContext handling time (before spectator/respawn).
 */
public final class CSDeathMarkerCapture {
    private CSDeathMarkerCapture() {
    }

    public static DeathMarkerEvent captureFromWorldPose(
            DeathMarkerLedger ledger,
            UUID playerId,
            String teamId,
            double x,
            double y,
            double z,
            float yaw,
            long deathTick,
            long ttlTicks,
            Optional<String> floorSlug
    ) {
        Objects.requireNonNull(ledger, "ledger");
        PlayerPoseSnapshot pose = new PlayerPoseSnapshot(
                playerId, teamId, x, y, z, yaw, deathTick, floorSlug, false
        );
        DeathMarkerEvent event = CSMapMinimapMarkerProvider.captureDeath(pose, deathTick, ttlTicks);
        ledger.record(event);
        return event;
    }
}