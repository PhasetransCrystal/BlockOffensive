package net.ptcrys.blockoffensive.map;

public final class CSEconomyRules {

    private CSEconomyRules() {}

    public static int calculateNextRoundMinMoney(Integer compensationFactor) {
        int defaultEconomy = 1400;
        int compensation = 500;
        if (compensationFactor == null) {
            return defaultEconomy;
        }
        return defaultEconomy + compensation * Math.max(0, compensationFactor - 1);
    }
}
