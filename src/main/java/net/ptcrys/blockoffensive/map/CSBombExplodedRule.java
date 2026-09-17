package net.ptcrys.blockoffensive.map;

import net.ptcrys.fpsmatch.core.map.BlastBombState;
import net.ptcrys.fpsmatch.core.match.RoundContext;
import net.ptcrys.fpsmatch.core.match.RoundLifecycle;
import net.ptcrys.fpsmatch.core.match.RoundResult;
import net.ptcrys.fpsmatch.core.match.RoundRuleWithContext;

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
