package com.ptcrys.blockoffensive.map;

import java.util.List;
import java.util.Objects;

/**
 * Pure CSDM FFA/TDM team relation helpers.
 * MapTeams.isSameTeam must not hardcode csdm=false; providers use runtime relation via these rules.
 */
public final class CSDMTeamSemantics {
    public static final int FFA_TEAM_CAPACITY = 1;
    public static final int TDM_TEAM_CAPACITY = 16;
    public static final String SPECTATOR = "spectator";

    private static final List<String> FFA_BASE_POOL = List.of("1", "2", "3", "4", "5");
    private static final List<String> TDM_POOL = List.of("1", "2");

    private CSDMTeamSemantics() {
    }

    public static int teamCapacity(boolean isTdm) {
        return isTdm ? TDM_TEAM_CAPACITY : FFA_TEAM_CAPACITY;
    }

    public static List<String> ffaBaseTeamPool() {
        return FFA_BASE_POOL;
    }

    public static List<String> tdmTeamPool() {
        return TDM_POOL;
    }

    /**
     * @param isTdm map is in team deathmatch mode
     * @param teamA runtime team name of player A (null if none)
     * @param teamB runtime team name of player B
     * @param aObserver true if A is spectator/observer identity
     * @param bObserver true if B is spectator/observer identity
     */
    public static boolean areTeammates(
            boolean isTdm,
            String teamA,
            String teamB,
            boolean aObserver,
            boolean bObserver
    ) {
        if (aObserver || bObserver) {
            return false;
        }
        if (!isTdm) {
            return false;
        }
        if (teamA == null || teamB == null) {
            return false;
        }
        if (SPECTATOR.equals(teamA) || SPECTATOR.equals(teamB)) {
            return false;
        }
        return Objects.equals(teamA, teamB);
    }
}