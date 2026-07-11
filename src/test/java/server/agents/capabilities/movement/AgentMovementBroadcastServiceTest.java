package server.agents.capabilities.movement;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMovementBroadcastServiceTest {
    @Test
    void unobservedMapSkipsPacketWorkAndInvalidatesDedupState() {
        MapleMap map = mock(MapleMap.class);
        when(map.isObservedByPlayer()).thenReturn(false);
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentMovementBroadcastStateRuntime.record(entry, 10, 20, 1, 2, 3, 4);

        AgentMovementBroadcastService.broadcastMovement(entry);

        assertFalse(AgentMovementBroadcastStateRuntime.matches(entry, 10, 20, 1, 2, 3, 4));
    }
}
