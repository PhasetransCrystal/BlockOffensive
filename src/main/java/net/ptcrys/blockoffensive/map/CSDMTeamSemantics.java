package net.ptcrys.blockoffensive.map;

import java.util.List;
import java.util.Objects;

/**
 * Pure CSDM roster and combat-relation helpers.
 */
public final class CSDMTeamSemantics {

    public static final int TEAM_CAPACITY = 16;
    public static final String SPECTATOR = "spectator";

    private static final List<String> TEAM_POOL = List.of("ct", "t");

    private CSDMTeamSemantics() {}

    public static List<String> teamPool() {
        return TEAM_POOL;
    }

    /**
     * @param isTdm     map is in team deathmatch mode
     * @param teamA     runtime team name of player A (null if none)
     * @param teamB     runtime team name of player B
     * @param aObserver true if A is spectator/observer identity
     * @param bObserver true if B is spectator/observer identity
     */
    public static boolean areTeammates(
                                       boolean isTdm,
                                       String teamA,
                                       String teamB,
                                       boolean aObserver,
                                       boolean bObserver) {
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
