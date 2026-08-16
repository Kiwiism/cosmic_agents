package server.agents.capabilities.townlife;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTownLifeAmbientStateTest {
    @Test
    void quotaPolicyFillsDeficitsWithoutExceedingHardCaps() {
        AgentTownLifeAmbientState state = new AgentTownLifeAmbientState();
        state.configure("deployment", policy());
        AgentTownLifeProfile profile = AgentTownLifeProfileRepository.defaultRepository()
                .require(100000000);
        List<AgentTownLifePopulationPort.AgentView> population = List.of(
                view(1, AgentTownLifeState.Activity.REST),
                view(2, AgentTownLifeState.Activity.REST),
                view(3, AgentTownLifeState.Activity.STROLL),
                view(4, AgentTownLifeState.Activity.STROLL));

        AgentTownLifeState.Activity chosen = state.choose(
                AgentTownLifeState.Activity.STROLL, profile, population);

        assertEquals(AgentTownLifeState.Activity.SOCIALIZE, chosen);
    }

    @Test
    void ambientDwellAndTransitionsAreBoundedAndDeterministic() {
        AgentTownLifeAmbientState state = new AgentTownLifeAmbientState();
        state.configure("deployment", policy());
        EnumSet<AgentTownLifeAmbientState.CompletionTransition> transitions =
                EnumSet.noneOf(AgentTownLifeAmbientState.CompletionTransition.class);

        for (int agentId = 1; agentId <= 500; agentId++) {
            long first = state.dwellDuration(agentId, 7, AgentTownLifeState.Activity.REST);
            long second = state.dwellDuration(agentId, 7, AgentTownLifeState.Activity.REST);
            assertEquals(first, second);
            assertTrue(first >= 20000L && first <= 180000L);
            transitions.add(state.completed(agentId, 7, AgentTownLifeState.Activity.REST));
        }

        assertEquals(EnumSet.allOf(AgentTownLifeAmbientState.CompletionTransition.class), transitions);
        assertEquals(3010000, state.preferredChairItemId(2));
        assertEquals(3010001, state.preferredChairItemId(3));
    }

    private static AgentTownLifePopulationPort.AgentView view(
            int id, AgentTownLifeState.Activity activity) {
        return new AgentTownLifePopulationPort.AgentView(
                id, 0, 1, 100000000, "", activity);
    }

    private static AgentTownLifeAmbientPolicy policy() {
        Map<AgentTownLifeState.Activity, AgentTownLifeAmbientPolicy.ActivityRule> rules =
                new EnumMap<>(AgentTownLifeState.Activity.class);
        rules.put(AgentTownLifeState.Activity.REST,
                new AgentTownLifeAmbientPolicy.ActivityRule(25, 2, 20000L, 180000L));
        rules.put(AgentTownLifeState.Activity.SOCIALIZE,
                new AgentTownLifeAmbientPolicy.ActivityRule(50, 4, 12000L, 60000L));
        rules.put(AgentTownLifeState.Activity.STROLL,
                new AgentTownLifeAmbientPolicy.ActivityRule(25, 2, 5000L, 18000L));
        return new AgentTownLifeAmbientPolicy(rules,
                new AgentTownLifeAmbientPolicy.TransitionWeights(35, 20, 40, 5),
                List.of(3010000, 3010001));
    }
}
