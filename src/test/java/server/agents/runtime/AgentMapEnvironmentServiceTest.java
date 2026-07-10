package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMapEnvironmentService;
import server.agents.integration.MapGateway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMapEnvironmentServiceTest {
    @Test
    void returnsFalseWhenAgentHasNoMap() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        MapGateway maps = mock(MapGateway.class);
        when(maps.isSwimMap(agent)).thenReturn(false);

        assertFalse(AgentMapEnvironmentService.isSwimMap(entry, maps));
    }

    @Test
    void readsSwimFlagFromAgentMap() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        MapGateway maps = mock(MapGateway.class);
        when(maps.isSwimMap(agent)).thenReturn(true);

        assertTrue(AgentMapEnvironmentService.isSwimMap(entry, maps));
    }
}
