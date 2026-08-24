package com.ptcrys.blockoffensive.minimap;

import com.ptcrys.fpsmatch.core.minimap.marker.DefaultTeamVisibilityPolicy;
import com.ptcrys.fpsmatch.core.minimap.marker.DeathMarkerEvent;
import com.ptcrys.fpsmatch.core.minimap.marker.MarkerCandidate;
import com.ptcrys.fpsmatch.core.minimap.marker.MarkerSnapshot;
import com.ptcrys.fpsmatch.core.minimap.marker.MinimapViewerContext;
import com.ptcrys.fpsmatch.core.minimap.marker.MinimapVisibilityPolicy;
import com.ptcrys.fpsmatch.core.minimap.marker.PlayerPoseSnapshot;
import com.ptcrys.fpsmatch.core.minimap.marker.ViewerRole;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CS/CSDM visibility: base team rules + shared LOS/fire intel + last-known.
 * Never serializes protected enemy candidates that fail the filter.
 */
public final class CSMinimapVisibilityPolicy implements MinimapVisibilityPolicy {
    private final DefaultTeamVisibilityPolicy base = new DefaultTeamVisibilityPolicy();
    private final CSMinimapIntelLedger intelLedger;
    private final CSMinimapVisibilityConfig config;

    public CSMinimapVisibilityPolicy(CSMinimapIntelLedger intelLedger, CSMinimapVisibilityConfig config) {
        this.intelLedger = Objects.requireNonNull(intelLedger, "intelLedger");
        this.config = Objects.requireNonNull(config, "config");
    }

    public CSMinimapVisibilityPolicy(CSMinimapIntelLedger intelLedger) {
        this(intelLedger, CSMinimapVisibilityConfig.DEFAULTS);
    }

    @Override
    public List<MarkerSnapshot.Marker> filter(MinimapViewerContext context, List<MarkerCandidate> candidates) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(candidates, "candidates");
        // FFA: no shared intel and no teammates — only self + public + own death events.
        List<MarkerSnapshot.Marker> visible;
        if (!context.teamSharedIntelEnabled() && context.role() != ViewerRole.SPECTATOR_TEAM) {
            visible = filterFfa(context, candidates);
        } else {
            visible = new ArrayList<>(base.filter(context, candidates));
            Set<String> already = visible.stream().map(m -> m.markerId().toString())
                    .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
            if (context.role() == ViewerRole.ACTIVE_PLAYER || context.role() == ViewerRole.DEAD_TEAM_MEMBER) {
                if (context.teamSharedIntelEnabled()) {
                    for (EnemyIntelState intel : intelLedger.intelForTeam(context.teamId())) {
                        MarkerSnapshot.Marker marker = toIntelMarker(intel);
                        if (already.add(marker.markerId().toString())) {
                            visible.add(marker);
                        }
                    }
                }
            }
        }
        return normalizeStyles(context, candidates, visible);
    }

    private static List<MarkerSnapshot.Marker> filterFfa(MinimapViewerContext context, List<MarkerCandidate> candidates) {
        List<MarkerSnapshot.Marker> visible = new ArrayList<>();
        for (MarkerCandidate candidate : candidates) {
            boolean isSelf = context.selfMarkerId().isPresent()
                    && context.selfMarkerId().get().equals(candidate.markerId());
            if (candidate.publicObjective()) {
                visible.add(candidate.toMarker());
                continue;
            }
            if (isSelf && !candidate.deathEvent()) {
                visible.add(candidate.toMarker());
                continue;
            }
            // own death event id is fpsmatch:event/death/<uuid> while self is fpsmatch:player/<uuid>
            if (candidate.deathEvent() && isOwnDeath(context, candidate)) {
                visible.add(candidate.toMarker());
            }
        }
        return MarkerSnapshot.of(visible).markers();
    }

    private static boolean isOwnDeath(MinimapViewerContext context, MarkerCandidate candidate) {
        if (context.selfMarkerId().isEmpty()) {
            return false;
        }
        String self = context.selfMarkerId().get().toString(); // fpsmatch:player/<uuid>
        String death = candidate.markerId().toString(); // fpsmatch:event/death/<uuid>
        int slash = self.lastIndexOf('/');
        if (slash < 0) {
            return false;
        }
        String uuid = self.substring(slash + 1);
        return death.endsWith("/" + uuid) || death.endsWith(uuid);
    }

    private static MarkerSnapshot.Marker toIntelMarker(EnemyIntelState intel) {
        NamespacedId id = PlayerPoseSnapshot.markerIdFor(intel.targetId());
        Optional<Long> expires = intel.isActive()
                ? Optional.empty()
                : Optional.of(intel.lastKnownUntilTick());
        return new MarkerSnapshot.Marker(
                id,
                PlayerPoseSnapshot.TYPE_ID,
                CSMinimapAssetCatalog.STYLE_ENEMY,
                intel.x(),
                intel.y(),
                intel.z(),
                intel.yaw(),
                intel.updatedTick(),
                expires,
                intel.floorSlug()
        );
    }

    private static List<MarkerSnapshot.Marker> normalizeStyles(
            MinimapViewerContext context,
            List<MarkerCandidate> candidates,
            List<MarkerSnapshot.Marker> visible
    ) {
        Map<NamespacedId, MarkerCandidate> byId = new LinkedHashMap<>();
        candidates.forEach(candidate -> byId.put(candidate.markerId(), candidate));
        List<MarkerSnapshot.Marker> normalized = new ArrayList<>(visible.size());
        for (MarkerSnapshot.Marker marker : visible) {
            MarkerCandidate source = byId.get(marker.markerId());
            NamespacedId style = marker.styleId();
            if ((source != null && source.deathEvent())
                    || marker.typeId().equals(DeathMarkerEvent.TYPE_ID)) {
                style = CSMinimapAssetCatalog.STYLE_DEATH;
            } else if (marker.typeId().equals(PlayerPoseSnapshot.TYPE_ID)) {
                boolean self = context.selfMarkerId().filter(marker.markerId()::equals).isPresent();
                if (self) {
                    style = CSMinimapAssetCatalog.STYLE_SELF;
                } else if (source != null && context.teamId().equals(source.teamId())) {
                    style = CSMinimapAssetCatalog.STYLE_ALLY;
                } else {
                    style = CSMinimapAssetCatalog.STYLE_ENEMY;
                }
            }
            normalized.add(withStyle(marker, style));
        }
        return MarkerSnapshot.of(normalized).markers();
    }

    private static MarkerSnapshot.Marker withStyle(
            MarkerSnapshot.Marker marker,
            NamespacedId style
    ) {
        return new MarkerSnapshot.Marker(
                marker.markerId(), marker.typeId(), style,
                marker.x(), marker.y(), marker.z(), marker.yaw(), marker.updatedTick(),
                marker.expiresTick(), marker.floorSlug(), marker.stateFields()
        );
    }
}
