package server.agents.progression;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentHuntRecoveryRuntimeTest {
    @Test
    void instanceExhaustionReentersOnceThenActivatesExternalFallback() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        String objective = "instructor:2195";

        assertEquals(AgentHuntRecoveryRuntime.Observation.STAY,
                AgentHuntRecoveryRuntime.observe(
                        entry, objective, 912030000, 35, 0, true, 1_000L));
        assertEquals(AgentHuntRecoveryRuntime.Observation.REENTER_INSTANCE,
                AgentHuntRecoveryRuntime.observe(
                        entry, objective, 912030000, 35, 0, true, 17_000L));
        assertEquals(AgentHuntRecoveryRuntime.Observation.STAY,
                AgentHuntRecoveryRuntime.observe(
                        entry, objective, 912030001, 35, 0, true, 18_000L));
        assertEquals(AgentHuntRecoveryRuntime.Observation.RESELECT,
                AgentHuntRecoveryRuntime.observe(
                        entry, objective, 912030001, 35, 0, true, 34_000L));

        AgentHuntRecoveryRuntime.failMaps(entry, objective, 35,
                Set.of(120010000, 912030000, 912030001), 40_000L);
        assertTrue(AgentHuntRecoveryRuntime.fallbackActive(entry, objective, 35, 40_001L));
        assertTrue(AgentHuntRecoveryRuntime.failedMaps(entry, objective, 35, 40_001L)
                .containsAll(Set.of(120010000, 912030000, 912030001)));
    }
}
