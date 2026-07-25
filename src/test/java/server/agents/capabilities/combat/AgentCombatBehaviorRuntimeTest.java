package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.behavior.AgentBehaviorCalibrationState;
import server.agents.behavior.AgentBehaviorPolicyProfile;
import server.agents.behavior.AgentBehaviorPolicyRepository;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentCombatBehaviorRuntimeTest {
    @Test
    void claimAvoidanceIsAProfileChanceRatherThanAnUnconditionalFilter() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        AgentBehaviorPolicyProfile policy =
                AgentBehaviorPolicyRepository.defaultRepository().resolve("efficient-v1");
        entry.capabilityStates().require(AgentBehaviorCalibrationState.STATE_KEY)
                .configure(policy, 91234L, true);
        Monster crowded = mock(Monster.class);
        Monster open = mock(Monster.class);
        List<Monster> candidates = List.of(crowded, open);
        Map<Monster, Integer> occupancy = Map.of(crowded, 10, open, 0);

        int avoidedClaims = 0;
        int acceptedClaims = 0;
        for (int attempt = 0; attempt < 1_000; attempt++) {
            if (AgentCombatBehaviorRuntime.respectClaims(entry, candidates, occupancy).size() == 1) {
                avoidedClaims++;
            } else {
                acceptedClaims++;
            }
        }

        assertTrue(avoidedClaims >= 250 && avoidedClaims <= 450);
        assertTrue(acceptedClaims > 0);
    }
}
