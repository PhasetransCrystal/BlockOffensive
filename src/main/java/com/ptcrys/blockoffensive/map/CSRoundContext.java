package com.ptcrys.blockoffensive.map;

import com.ptcrys.fpsmatch.core.map.BlastBombState;
import com.ptcrys.fpsmatch.core.match.RoundContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CS 爆破模式每 tick 注入到通用回合生命周期的上下文接口。
 * 测试可提供 fake 实现，避免依赖真实 CSGameMap。
 */
public interface CSRoundContext extends RoundContext {
    BlastBombState blastState();

    Map<String, List<UUID>> livingTeams();

    boolean hasEnoughPlayers();

    boolean canPlaceBombs(String teamName);

    String tTeamName();

    String ctTeamName();

    boolean isDebug();
}
