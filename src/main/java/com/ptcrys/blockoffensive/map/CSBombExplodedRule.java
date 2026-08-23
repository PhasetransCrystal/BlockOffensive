package com.ptcrys.blockoffensive.map;

import com.ptcrys.fpsmatch.core.map.BlastBombState;
import com.ptcrys.fpsmatch.core.match.RoundLifecycle;
import com.ptcrys.fpsmatch.core.match.RoundResult;
import com.ptcrys.fpsmatch.core.match.RoundRuleWithContext;
import com.ptcrys.fpsmatch.core.match.RoundContext;

import java.util.Optional;

class CSBombExplodedRule implements RoundRuleWithContext<String, CSRoundResultReason> {
    @Override
    public Optional<RoundResult<String, CSRoundResultReason>> evaluate(RoundLifecycle<String, CSRoundResultReason> lifecycle, RoundContext context) {
        CSRoundContext ctx = (CSRoundContext) context;
        if (ctx.blastState() == BlastBombState.EXPLODED) {
            return Optional.of(new RoundResult<>(ctx.tTeamName(), CSRoundResultReason.DETONATE_BOMB));
        }
        return Optional.empty();
    }
}
