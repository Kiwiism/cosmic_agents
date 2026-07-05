package server.agents.capabilities.supplies;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeHandle;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentGroupSupplyResponderSelectorTest {
    @Test
    void returnsNullForMissingEntries() {
        Character leader = character(100);

        assertNull(AgentGroupSupplyResponderSelector.select(leader, null, TestHandle::mapId));
        assertNull(AgentGroupSupplyResponderSelector.select(leader, List.of(), TestHandle::mapId));
    }

    @Test
    void prefersAgentInLeaderCurrentMap() {
        Character leader = character(100);
        TestHandle first = new TestHandle(200);
        TestHandle sameMap = new TestHandle(100);

        TestHandle selected = AgentGroupSupplyResponderSelector.select(leader, List.of(first, sameMap), TestHandle::mapId);

        assertSame(sameMap, selected);
    }

    @Test
    void fallsBackToFirstEntryWhenNoAgentInLeaderMap() {
        Character leader = character(100);
        TestHandle first = new TestHandle(200);
        TestHandle second = new TestHandle(300);

        TestHandle selected = AgentGroupSupplyResponderSelector.select(leader, List.of(first, second), TestHandle::mapId);

        assertSame(first, selected);
    }

    private static Character character(int mapId) {
        Character character = mock(Character.class);
        when(character.getMapId()).thenReturn(mapId);
        return character;
    }

    private record TestHandle(int mapId) implements AgentRuntimeHandle {
    }
}
