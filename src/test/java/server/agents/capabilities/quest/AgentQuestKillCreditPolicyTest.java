package server.agents.capabilities.quest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentQuestKillCreditPolicyTest {
    @Test
    void grantsCreditToTheEligibleAgentWithTheMostExactDamage() {
        Map<Integer, Long> damage = Map.of(10, 12L, 20, 37L, 30, 99L);

        int winner = AgentQuestKillCreditPolicy.highestDamageAgentId(
                damage.keySet(), Set.of(10, 20)::contains, id -> damage.get(id));

        assertEquals(20, winner);
    }

    @Test
    void breaksEqualDamageTiesDeterministicallyAndIgnoresMissingAgents() {
        Map<Integer, Long> damage = Map.of(10, 25L, 20, 25L);

        assertEquals(10, AgentQuestKillCreditPolicy.highestDamageAgentId(
                damage.keySet(), id -> true, id -> damage.get(id)));
        assertEquals(-1, AgentQuestKillCreditPolicy.highestDamageAgentId(
                List.of(10, 20), id -> false, id -> damage.get(id)));
    }
}
