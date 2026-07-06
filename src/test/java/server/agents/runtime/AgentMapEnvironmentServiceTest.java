package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.maps.MapleMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMapEnvironmentServiceTest {
    @Test
    void returnsFalseWhenAgentHasNoMap() {
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(null);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);

        assertFalse(AgentMapEnvironmentService.isSwimMap(entry));
    }

    @Test
    void readsSwimFlagFromAgentMap() {
        Character agent = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        when(agent.getMap()).thenReturn(map);
        when(map.isSwim()).thenReturn(true);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);

        assertTrue(AgentMapEnvironmentService.isSwimMap(entry));
    }
}
