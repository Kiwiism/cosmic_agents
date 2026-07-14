package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRuntimeRegistryAdmissionTest {
    @AfterEach
    void tearDown() {
        AgentRuntimeRegistry.clear();
        System.clearProperty("agents.scheduler.loadShedding.enabled");
        System.clearProperty("agents.scheduler.loadShedding.maxActiveAgents");
    }

    @Test
    void enforcesPopulationLimitAtomicallyButAllowsSessionReplacement() {
        System.setProperty("agents.scheduler.loadShedding.enabled", "true");
        System.setProperty("agents.scheduler.loadShedding.maxActiveAgents", "1");
        AgentRuntimeEntry first = entry(101);
        AgentRuntimeRegistry.registerEntry(1, first);

        assertThrows(RejectedExecutionException.class,
                () -> AgentRuntimeRegistry.registerEntry(1, entry(102)));

        AgentRuntimeEntry replacement = entry(101);
        AgentRuntimeRegistry.registerEntry(2, replacement);
        assertEquals(1, AgentRuntimeRegistry.activeAgentCount());
        assertEquals(replacement, AgentRuntimeRegistry.findByAgentCharacterId(101));
    }

    private static AgentRuntimeEntry entry(int id) {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(id);
        return new AgentRuntimeEntry(agent, null, null);
    }
}
