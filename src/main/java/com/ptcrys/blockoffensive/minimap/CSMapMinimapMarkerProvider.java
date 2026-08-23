package com.ptcrys.blockoffensive.minimap;

import com.ptcrys.fpsmatch.core.minimap.marker.DeathMarkerEvent;
import com.ptcrys.fpsmatch.core.minimap.marker.DeathMarkerLedger;
import com.ptcrys.fpsmatch.core.minimap.marker.MarkerCandidate;
import com.ptcrys.fpsmatch.core.minimap.marker.MinimapMarkerProvider;
import com.ptcrys.fpsmatch.core.minimap.marker.MinimapViewerContext;
import com.ptcrys.fpsmatch.core.minimap.marker.PlayerPoseSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Player + death markers for CS and CSDM. Visibility filtering remains in MinimapVisibilityPolicy.
 */
public final class CSMapMinimapMarkerProvider implements MinimapMarkerProvider {
    public static final long CS_DEATH_TTL_TICKS = 100L;
    public static final long CSDM_DEATH_TTL_TICKS = 40L;

    private final Supplier<List<PlayerPoseSnapshot>> livingPlayers;
    private final DeathMarkerLedger deathLedger;
    private final LongSupplier nowTick;

    public CSMapMinimapMarkerProvider(
            Supplier<List<PlayerPoseSnapshot>> livingPlayers,
            DeathMarkerLedger deathLedger,
            LongSupplier nowTick
    ) {
        this.livingPlayers = Objects.requireNonNull(livingPlayers, "livingPlayers");
        this.deathLedger = Objects.requireNonNull(deathLedger, "deathLedger");
        this.nowTick = Objects.requireNonNull(nowTick, "nowTick");
    }

    @Override
    public List<MarkerCandidate> collect(MinimapViewerContext context) {
        Objects.requireNonNull(context, "context");
        long tick = nowTick.getAsLong();
        deathLedger.purgeExpired(tick);
        List<MarkerCandidate> out = new ArrayList<>();
        for (PlayerPoseSnapshot pose : livingPlayers.get()) {
            if (pose.living()) {
                out.add(pose.toLivingCandidate());
            }
        }
        for (DeathMarkerEvent death : deathLedger.activeAt(tick)) {
            out.add(death.toCandidate());
        }
        return List.copyOf(out);
    }

    public static DeathMarkerEvent captureDeath(
            PlayerPoseSnapshot lastPose,
            long deathTick,
            long ttlTicks
    ) {
        Objects.requireNonNull(lastPose, "lastPose");
        if (ttlTicks <= 0) {
            throw new IllegalArgumentException("ttlTicks must be positive");
        }
        return new DeathMarkerEvent(
                lastPose.playerId(),
                lastPose.teamId(),
                lastPose.x(),
                lastPose.y(),
                lastPose.z(),
                lastPose.yaw(),
                deathTick,
                deathTick + ttlTicks,
                lastPose.floorSlug()
        );
    }
}