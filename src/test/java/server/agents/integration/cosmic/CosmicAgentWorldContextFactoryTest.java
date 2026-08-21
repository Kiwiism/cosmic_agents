package server.agents.integration.cosmic;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.world.AgentWorldContext;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CosmicAgentWorldContextFactoryTest {
    @Test
    void captureDoesNotRegisterOrMutateAgentCapabilityState() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(27);
        when(agent.getName()).thenReturn("KiwiAgent");
        when(agent.getLevel()).thenReturn(15);
        when(agent.getJob()).thenReturn(Job.WARRIOR);
        when(agent.getMapId()).thenReturn(100_000_000);
        when(agent.getHp()).thenReturn(100);
        when(agent.getMaxHp()).thenReturn(100);
        when(agent.getMp()).thenReturn(50);
        when(agent.getMaxMp()).thenReturn(50);
        when(agent.getMeso()).thenReturn(1_000);
        when(agent.isAlive()).thenReturn(true);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        Set<String> before = entry.capabilityStates().registeredStateIds();

        AgentWorldContext context =
                CosmicAgentWorldContextFactory.capture(entry, agent, 1_000L);

        assertEquals(before, entry.capabilityStates().registeredStateIds());
        assertTrue(context.evidence().containsKey("captureMode"));
        assertEquals("read-only", context.evidence().get("captureMode"));
    }
}
