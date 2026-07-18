package server.agents.objectives;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSessionEventRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentObjectiveKernelTest {
    @Test
    void recordsAndPublishesTheObjectiveLifecycle() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(41);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentObjectiveDefinition objective = new AgentObjectiveDefinition(
                "maple-41-1", "quest", 10, 10_000L, 2,
                AgentObjectiveSource.QUEST_PLAN, "maple-v1", "run-1");

        AgentObjectiveKernel.start(entry, objective, 100L);
        assertEquals(objective, AgentObjectiveKernel.active(entry));
        assertTrue(AgentObjectiveKernel.transition(entry, objective.objectiveId(),
                AgentObjectiveStatus.SUCCEEDED, "quest complete", 200L));

        assertNull(AgentObjectiveKernel.active(entry));
        AgentObjectiveState state = entry.capabilityStates().require(AgentObjectiveState.STATE_KEY);
        assertEquals(2, state.journalSnapshot().size());
        assertEquals(AgentObjectiveStatus.ACTIVE, state.journalSnapshot().get(0).status());
        assertEquals(AgentObjectiveStatus.SUCCEEDED, state.journalSnapshot().get(1).status());
        assertEquals(2, AgentSessionEventRuntime.bus(entry).snapshot().queued());
        assertFalse(AgentObjectiveKernel.transition(entry, objective.objectiveId(),
                AgentObjectiveStatus.FAILED, "late failure", 300L));
    }
}
