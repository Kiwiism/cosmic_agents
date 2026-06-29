package server.agents.capabilities.combat;

import java.awt.Rectangle;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import server.agents.capabilities.combat.AgentAttackExecutionProvider.BasicAttackData;
import server.life.Monster;

public final class AgentBasicAttackPlanner {
    public record BasicAttackSelection(Monster target, BasicAttackData attackData) {
    }

    private AgentBasicAttackPlanner() {
    }

    public static BasicAttackSelection selectBasicAttack(
            Monster initialTarget,
            Function<Monster, BasicAttackData> buildAttackData,
            BiFunction<Monster, Rectangle, Monster> resolveEffectivePrimary,
            BiPredicate<Rectangle, Monster> intersectsMonster,
            Function<Monster, Monster> findReachableOnOppositeFacing) {
        BasicAttackData attackData = buildAttackData.apply(initialTarget);
        Monster effective = resolveEffectivePrimary.apply(initialTarget, attackData.hitBox());
        if (effective != initialTarget) {
            attackData = buildAttackData.apply(effective);
        }
        if (!intersectsMonster.test(attackData.hitBox(), effective)) {
            Monster pivoted = findReachableOnOppositeFacing.apply(initialTarget);
            if (pivoted == null) {
                return null;
            }
            attackData = buildAttackData.apply(pivoted);
            effective = pivoted;
        }
        return new BasicAttackSelection(effective, attackData);
    }
}
