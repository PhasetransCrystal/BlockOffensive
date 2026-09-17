package net.ptcrys.blockoffensive.data;

/** Standalone regression scenarios for halftime, overtime, reconnect snapshots and rematches. */
public final class CSScoreboardHistoryCheck {
    public static void main(String[] args) {
        CSScoreboardHistory history = new CSScoreboardHistory();
        // The original CT roster wins 8 of the opening 12 rounds, then becomes T.
        for (int i = 0; i < 8; i++) history.add(true, CSScoreboardHistory.DEFUSE);
        for (int i = 0; i < 4; i++) history.add(false, CSScoreboardHistory.EXPLOSION);
        int[] reconnectSnapshot = history.snapshot();
        history.switchSides();
        check(CSScoreboardHistory.wins(history.snapshot(), true, 0, 12) == 4, "CT first half must follow the new roster");
        check(CSScoreboardHistory.wins(history.snapshot(), false, 0, 12) == 8, "T first half retains its eight wins");
        check(history.snapshot()[0] == -CSScoreboardHistory.DEFUSE, "switch preserves the win reason");
        check(reconnectSnapshot[0] == CSScoreboardHistory.DEFUSE, "published snapshots must not change after a switch");

        // The second half reaches 12-12; an overtime side switch must keep both halves intact.
        for (int i = 0; i < 8; i++) history.add(true, CSScoreboardHistory.ELIMINATION);
        for (int i = 0; i < 4; i++) history.add(false, CSScoreboardHistory.TIMEOUT);
        history.switchSides();
        history.add(true, CSScoreboardHistory.ELIMINATION);
        check(CSScoreboardHistory.wins(history.snapshot(), true, 0, 12) == 8, "overtime flips the first half back");
        check(CSScoreboardHistory.wins(history.snapshot(), true, 12, 24) == 4, "overtime is excluded from second-half totals");
        check(CSScoreboardHistory.wins(history.snapshot(), true, 0, 25) == 13, "late join sees the full overtime score");
        history.reset();
        history.add(false, CSScoreboardHistory.EXPLOSION);
        check(history.snapshot().length == 1 && history.snapshot()[0] == -4, "new match cannot inherit old rounds");
        System.out.println("CS scoreboard history checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
