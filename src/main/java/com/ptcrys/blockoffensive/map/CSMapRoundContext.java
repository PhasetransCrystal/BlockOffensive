package com.ptcrys.blockoffensive.map;

import com.ptcrys.fpsmatch.core.map.BlastBombState;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 基于真实 CSGameMap 的 CSRoundContext 实现。
 */
class CSMapRoundContext implements CSRoundContext {
    private final CSGameMap map;

    CSMapRoundContext(CSGameMap map) {
        this.map = map;
    }

    @Override
    public BlastBombState blastState() {
        return map.blastState();
    }

    @Override
    public Map<String, List<UUID>> livingTeams() {
        return map.getMapTeams().getTeamsLiving().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().getFixedName(), Map.Entry::getValue));
    }

    @Override
    public boolean hasEnoughPlayers() {
        return map.getMapTeams().getJoinedPlayers().size() != 1;
    }

    @Override
    public boolean canPlaceBombs(String teamName) {
        return map.checkCanPlacingBombs(teamName);
    }

    @Override
    public String tTeamName() {
        return map.getT().getFixedName();
    }

    @Override
    public String ctTeamName() {
        return map.getCT().getFixedName();
    }

    @Override
    public boolean isDebug() {
        return map.isDebug();
    }
}
