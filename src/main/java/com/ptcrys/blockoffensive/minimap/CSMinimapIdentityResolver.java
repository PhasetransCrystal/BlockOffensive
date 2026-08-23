package com.ptcrys.blockoffensive.minimap;

import com.ptcrys.fpsmatch.core.minimap.marker.MinimapViewerContext;
import com.ptcrys.fpsmatch.core.minimap.marker.PlayerPoseSnapshot;
import com.ptcrys.fpsmatch.core.minimap.marker.ViewerRole;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves viewer identity for CS / CSDM minimap streams.
 * Camera target is intentionally ignored: it never grants target-team intelligence.
 */
public final class CSMinimapIdentityResolver {
    public static final String SPECTATOR_TEAM = "spectator";

    private CSMinimapIdentityResolver() {
    }

    public static MinimapViewerContext resolve(
            UUID viewerId,
            String runtimeTeamId,
            boolean living,
            boolean spectatorTeam,
            boolean teamSharedIntelEnabled,
            boolean observerOmniscient
    ) {
        Objects.requireNonNull(viewerId, "viewerId");
        String teamId = runtimeTeamId == null || runtimeTeamId.isBlank() ? SPECTATOR_TEAM : runtimeTeamId;
        ViewerRole role;
        if (spectatorTeam || SPECTATOR_TEAM.equals(teamId)) {
            role = ViewerRole.SPECTATOR_TEAM;
            teamId = SPECTATOR_TEAM;
        } else if (!living) {
            role = ViewerRole.DEAD_TEAM_MEMBER;
        } else {
            role = ViewerRole.ACTIVE_PLAYER;
        }
        return new MinimapViewerContext(
                role,
                teamId,
                Optional.of(PlayerPoseSnapshot.markerIdFor(viewerId)),
                teamSharedIntelEnabled,
                observerOmniscient
        );
    }

    /**
     * CSDM FFA never shares team intel; TDM and CS share within runtime team.
     */
    public static boolean teamSharedIntelFor(boolean csdm, boolean csdmTdm) {
        if (!csdm) {
            return true;
        }
        return csdmTdm;
    }
}