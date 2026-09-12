package com.ptcrys.blockoffensive.map;

import java.util.List;

/** Standalone deathmatch regression check; requires only the JDK. */
public final class CSDMLogicCheck {
    public static void main(String[] args) {
        expect(CSDMTeamSemantics.teamPool(), List.of("ct", "t"), "deathmatch team pool");
        expect(CSDMTeamSemantics.TEAM_CAPACITY, 16, "team capacity");
        expect(CSDMTeamSemantics.areTeammates(false, "ct", "ct", false, false), false, "FFA same roster team");
        expect(CSDMTeamSemantics.areTeammates(false, "ct", "t", false, false), false, "FFA opposite roster team");
        expect(CSDMTeamSemantics.areTeammates(true, "ct", "ct", false, false), true, "TDM same roster team");
        expect(CSDMTeamSemantics.areTeammates(true, "ct", "t", false, false), false, "TDM opposite roster team");
        expect(CSDMTeamSemantics.areTeammates(true, "ct", "ct", true, false), false, "spectator relation");

        expectScore("knife is handled by the item compatibility layer", 10);
        expectScore("cs2guns:glock_17_fade", 18);
        expectScore("cs2guns:p250_asiimov", 16);
        expectScore("cs2guns:deagle_blaze", 14);
        expectScore("cs2guns:mp9_hot_rod", 13);
        expectScore("cs2guns:ssg_08_dragonfire", 11);
        expectScore("cs2guns:ak47_redline", 10);
        expectScore("cs2guns:awp_dragon_lore", 8);
        System.out.println("CSDM logic checks passed");
    }

    private static void expectScore(String weaponPath, int expected) {
        expect(CSDMScoring.scoreForWeaponPath(weaponPath), expected, weaponPath);
    }

    private static void expect(Object actual, Object expected, String label) {
        if (!java.util.Objects.equals(actual, expected)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
