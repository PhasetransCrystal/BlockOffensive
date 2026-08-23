package com.ptcrys.blockoffensive.map;

import com.ptcrys.fpsmatch.core.map.BlastBombState;
import com.ptcrys.fpsmatch.core.match.RoundContext;
import com.ptcrys.fpsmatch.core.match.RoundLifecycle;
import com.ptcrys.fpsmatch.core.match.RoundResult;
import com.ptcrys.fpsmatch.core.match.RoundRuleWithContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

class CSEliminationRule implements RoundRuleWithContext<String, CSRoundResultReason> {
    @Override
    public Optional<RoundResult<String, CSRoundResultReason>> evaluate(RoundLifecycle<String, CSRoundResultReason> lifecycle, RoundContext context) {
        CSRoundContext ctx = (CSRoundContext) context;
        if (!ctx.hasEnoughPlayers() || ctx.isDebug()) {
            return Optional.empty();
        }

        boolean requireBombPlacer = ctx.blastState() == BlastBombState.TICKING;
        Map<String, List<UUID>> living = ctx.livingTeams();

        if (living.size() == 1) {
            String winner = living.keySet().iterator().next();
            if (requireBombPlacer && !ctx.canPlaceBombs(winner)) {
                return Optional.empty();
            }
            return Optional.of(new RoundResult<>(winner, CSRoundResultReason.ACED));
        }

        if (living.isEmpty()) {
            String winner = requireBombPlacer ? ctx.tTeamName() : ctx.ctTeamName();
            return Optional.of(new RoundResult<>(winner, CSRoundResultReason.ACED));
        }

        return Optional.empty();
    }
}
