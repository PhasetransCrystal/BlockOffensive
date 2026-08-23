package com.ptcrys.blockoffensive.map;

import com.ptcrys.fpsmatch.core.map.BlastBombState;
import com.ptcrys.fpsmatch.core.match.RoundLifecycle;
import com.ptcrys.fpsmatch.core.match.RoundResult;
import com.ptcrys.fpsmatch.core.match.RoundRuleWithContext;
import com.ptcrys.fpsmatch.core.match.RoundContext;

import java.util.Optional;

class CSBombDefusedRule implements RoundRuleWithContext<String, CSRoundResultReason> {
    @Override
    public Optional<RoundResult<String, CSRoundResultReason>> evaluate(RoundLifecycle<String, CSRoundResultReason> lifecycle, RoundContext context) {
        CSRoundContext ctx = (CSRoundContext) context;
        if (ctx.blastState() == BlastBombState.DEFUSED) {
            return Optional.of(new RoundResult<>(ctx.ctTeamName(), CSRoundResultReason.DEFUSE_BOMB));
        }
        return Optional.empty();
    }
}
