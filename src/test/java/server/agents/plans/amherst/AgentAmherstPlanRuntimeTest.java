package server.agents.plans.amherst;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentAmherstPlanRuntimeTest {
    @Test
    void completedPlanKeepsSeatedAgentOutOfOrdinaryMovementTicks() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        entry.amherstPlanExecutionState().completed = true;
        when(agent.getChair()).thenReturn(3010000);

        assertTrue(AgentAmherstPlanRuntime.tickGate(entry, agent, 100L));
    }

    @Test
    void completedPlanDoesNotConsumeTicksAfterAgentStands() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        entry.amherstPlanExecutionState().completed = true;
        when(agent.getChair()).thenReturn(-1);

        assertFalse(AgentAmherstPlanRuntime.tickGate(entry, agent, 100L));
    }
}
