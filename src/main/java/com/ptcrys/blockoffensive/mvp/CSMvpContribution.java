package com.ptcrys.blockoffensive.mvp;

import java.util.UUID;

public record CSMvpContribution(
        UUID uuid,
        int kills,
        int assists,
        float damage,
        int headshotKills,
        int grenadeKills,
        int flashedEnemies,
        float incendiaryDamage,
        float explosiveDamage,
        boolean defusedBomb,
        boolean plantedBomb,
        boolean survivedUnderFire
) {
}
