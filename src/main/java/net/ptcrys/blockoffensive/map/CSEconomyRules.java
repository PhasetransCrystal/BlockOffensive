package net.ptcrys.blockoffensive.map;

public final class CSEconomyRules {

    public static final int MAX_LOSS_FACTOR = 5;
    public static final int PISTOL_LOSS_REWARD = 1900;
    public static final int PLANTED_BOMB_LOSS_BONUS = 800;

    private CSEconomyRules() {}

    public static int calculateNextRoundMinMoney(Integer compensationFactor) {
        return calculateNextRoundMinMoney(compensationFactor, 1400, 500);
    }

    public static int calculateNextRoundMinMoney(Integer compensationFactor, int base, int increment) {
        return base + increment * Math.min(MAX_LOSS_FACTOR - 1, Math.max(0, compensationFactor == null ? 0 : compensationFactor));
    }

    public static int calculateLossReward(int compensationFactor, int base, int increment, boolean pistolRound) {
        if (pistolRound) {
            return PISTOL_LOSS_REWARD;
        }
        return base + increment * Math.min(MAX_LOSS_FACTOR - 1, Math.max(0, compensationFactor - 1));
    }
}
