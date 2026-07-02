package server.agents.capabilities.supplies;

import client.Character;
import org.junit.jupiter.api.Test;
import server.bots.BotEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentGroupSupplyResponderSelectorTest {
    @Test
    void returnsNullForMissingEntries() {
        Character leader = character(100);

        assertNull(AgentGroupSupplyResponderSelector.select(leader, null));
        assertNull(AgentGroupSupplyResponderSelector.select(leader, List.of()));
    }

    @Test
    void prefersAgentInLeaderCurrentMap() {
        Character leader = character(100);
        BotEntry first = entryWithAgentMap(200);
        BotEntry sameMap = entryWithAgentMap(100);

        BotEntry selected = AgentGroupSupplyResponderSelector.select(leader, List.of(first, sameMap));

        assertSame(sameMap, selected);
    }

    @Test
    void fallsBackToFirstEntryWhenNoAgentInLeaderMap() {
        Character leader = character(100);
        BotEntry first = entryWithAgentMap(200);
        BotEntry second = entryWithAgentMap(300);

        BotEntry selected = AgentGroupSupplyResponderSelector.select(leader, List.of(first, second));

        assertSame(first, selected);
    }

    private static Character character(int mapId) {
        Character character = mock(Character.class);
        when(character.getMapId()).thenReturn(mapId);
        return character;
    }

    private static BotEntry entryWithAgentMap(int mapId) {
        Character agent = character(mapId);
        return new BotEntry(agent, mock(Character.class), null);
    }
}
