package net.ptcrys.blockoffensive.data;

import java.util.ArrayList;
import java.util.List;

/** Round results follow the roster through side switches; magnitude records the win reason. */
public final class CSScoreboardHistory {

    public static final int ELIMINATION = 1;
    public static final int TIMEOUT = 2;
    public static final int DEFUSE = 3;
    public static final int EXPLOSION = 4;
    private final List<Integer> rounds = new ArrayList<>();

    public void add(boolean ctWinner, int reason) {
        if (reason < ELIMINATION || reason > EXPLOSION) throw new IllegalArgumentException("Unknown round result");
        rounds.add(ctWinner ? reason : -reason);
    }

    public void switchSides() {
        rounds.replaceAll(result -> -result);
    }

    public int[] snapshot() {
        return rounds.stream().mapToInt(Integer::intValue).toArray();
    }

    public void reset() {
        rounds.clear();
    }

    public static int wins(int[] rounds, boolean ct, int from, int to) {
        int wins = 0;
        for (int i = Math.max(0, from); i < Math.min(rounds.length, to); i++) {
            if (rounds[i] != 0 && (rounds[i] > 0) == ct) wins++;
        }
        return wins;
    }
}
