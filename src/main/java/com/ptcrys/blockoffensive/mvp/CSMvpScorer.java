package com.ptcrys.blockoffensive.mvp;

import java.util.Comparator;
import java.util.List;

public final class CSMvpScorer {
    private CSMvpScorer() {
    }

    public static CSMvpResult selectRoundMvp(List<CSMvpContribution> contributions) {
        return contributions.stream()
                .max(Comparator.comparingInt(CSMvpScorer::score)
                        .thenComparingInt(CSMvpScorer::priority)
                        .thenComparingDouble(CSMvpContribution::damage)
                        .thenComparingInt(contribution -> contribution.incendiaryDamage() > 0 ? 1 : 0))
                .map(contribution -> new CSMvpResult(
                        contribution.uuid(),
                        reasonKey(contribution),
                        infoKey(contribution),
                        score(contribution)
                ))
                .orElse(null);
    }

    private static int score(CSMvpContribution contribution) {
        int objectiveScore = 0;
        if (contribution.defusedBomb() || contribution.plantedBomb()) {
            objectiveScore = 300;
        } else if (contribution.survivedUnderFire()) {
            // Clutch survive is valuable but ranks below pure objective win actions.
            objectiveScore = 220;
        }
        float utilityDamage = contribution.incendiaryDamage() + contribution.explosiveDamage();
        float directDamage = Math.max(0.0F, contribution.damage() - utilityDamage);
        return objectiveScore + contribution.kills() * 3 + contribution.assists()
                + contribution.headshotKills()
                + Math.round(directDamage / 50.0F)
                + Math.round(contribution.incendiaryDamage() / 25.0F)
                + Math.round(contribution.explosiveDamage() / 45.0F);
    }

    private static int priority(CSMvpContribution contribution) {
        if (contribution.defusedBomb()) {
            return 400;
        }
        if (contribution.plantedBomb()) {
            return 300;
        }
        if (contribution.survivedUnderFire()) {
            return 260;
        }
        if (contribution.incendiaryDamage() > 0) {
            return 200;
        }
        if (contribution.explosiveDamage() > 0) {
            return 150;
        }
        if (contribution.damage() > 0) {
            return 100;
        }
        return 0;
    }

    /**
     * Narrative reason from the dominant contribution bucket.
     * Objective > clutch survive > dominant utility > combat damage.
     */
    private static String reasonKey(CSMvpContribution contribution) {
        if (contribution.defusedBomb()) {
            return "blockoffensive.mvp.defuse";
        }
        if (contribution.plantedBomb()) {
            return "blockoffensive.mvp.detonate";
        }
        if (contribution.survivedUnderFire()) {
            return "blockoffensive.mvp.survive_under_fire";
        }

        float utilityDamage = contribution.incendiaryDamage() + contribution.explosiveDamage();
        float directDamage = Math.max(0.0F, contribution.damage() - utilityDamage);
        int combatScore = contribution.kills() * 3
                + contribution.assists()
                + contribution.headshotKills()
                + Math.round(directDamage / 50.0F);
        int fireScore = Math.round(contribution.incendiaryDamage() / 25.0F);
        int explosiveScore = Math.round(contribution.explosiveDamage() / 45.0F);

        if (fireScore > 0 && fireScore >= explosiveScore && fireScore >= combatScore) {
            return "blockoffensive.mvp.incendiary_damage";
        }
        if (explosiveScore > 0 && explosiveScore >= combatScore) {
            return "blockoffensive.mvp.explosive_damage";
        }
        if (contribution.damage() > 0 || contribution.kills() > 0 || contribution.assists() > 0) {
            return "blockoffensive.mvp.high_damage";
        }
        return "blockoffensive.mvp.combat";
    }

    private static String infoKey(CSMvpContribution contribution) {
        return switch (reasonKey(contribution)) {
            case "blockoffensive.mvp.defuse" -> "blockoffensive.mvp.info.defuse";
            case "blockoffensive.mvp.detonate" -> "blockoffensive.mvp.info.detonate";
            case "blockoffensive.mvp.survive_under_fire" -> "blockoffensive.mvp.info.survive_under_fire";
            case "blockoffensive.mvp.incendiary_damage" -> "blockoffensive.mvp.info.incendiary_damage";
            case "blockoffensive.mvp.explosive_damage" -> "blockoffensive.mvp.info.explosive_damage";
            case "blockoffensive.mvp.high_damage" -> "blockoffensive.mvp.info.high_damage";
            default -> "blockoffensive.mvp.info.combat";
        };
    }
}
