package net.ptcrys.blockoffensive.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CSEconomyRulesTest {

    @Test
    void lossRewardsReachAndStayAtTheCs2Cap() {
        int[] expected = {1400, 1900, 2400, 2900, 3400, 3400};
        for (int factor = 1; factor <= expected.length; factor++) {
            assertEquals(expected[factor - 1],
                    CSEconomyRules.calculateLossReward(factor, 1400, 500, false));
        }
    }

    @Test
    void pistolLossAndNextRoundPredictionUseTheirOwnTiers() {
        assertEquals(1900, CSEconomyRules.calculateLossReward(1, 1400, 500, true));
        assertEquals(1400, CSEconomyRules.calculateNextRoundMinMoney(0));
        assertEquals(1900, CSEconomyRules.calculateNextRoundMinMoney(1));
        assertEquals(3400, CSEconomyRules.calculateNextRoundMinMoney(4));
        assertEquals(3400, CSEconomyRules.calculateNextRoundMinMoney(5));
        assertEquals(2000, CSEconomyRules.calculateNextRoundMinMoney(1, 1500, 500));
    }
}
