package server.agents.capabilities.quest;

import java.util.Collection;
import java.util.function.IntPredicate;
import java.util.function.IntToLongFunction;

/** Selects one Agent for quest kill credit without changing normal EXP distribution. */
public final class AgentQuestKillCreditPolicy {
    private AgentQuestKillCreditPolicy() {
    }

    public static int highestDamageAgentId(Collection<Integer> attackerIds,
                                           IntPredicate eligibleAgent,
                                           IntToLongFunction exactDamage) {
        int winnerId = -1;
        long winnerDamage = Long.MIN_VALUE;
        if (attackerIds == null) {
            return winnerId;
        }
        for (Integer attackerId : attackerIds) {
            if (attackerId == null || !eligibleAgent.test(attackerId)) {
                continue;
            }
            long damage = exactDamage.applyAsLong(attackerId);
            if (damage > winnerDamage || damage == winnerDamage
                    && (winnerId < 0 || attackerId < winnerId)) {
                winnerDamage = damage;
                winnerId = attackerId;
            }
        }
        return winnerId;
    }
}
