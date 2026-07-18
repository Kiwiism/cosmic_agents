package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLifecycleTransitionServiceTest {
    @Test
    void ownsStateMutationAndLifecycleEventEmission() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(52);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        assertTrue(AgentLifecycleTransitionService.transition(entry,
                AgentLifecyclePhase.QUIESCING, "despawn requested"));

        assertEquals(AgentLifecyclePhase.QUIESCING, entry.lifecycleState().phase());
        assertEquals("despawn requested", entry.lifecycleState().reason());
        assertEquals(1L, entry.lifecycleState().sequence());
        assertEquals(1, AgentSessionEventRuntime.bus(entry).snapshot().queued());
    }
}
