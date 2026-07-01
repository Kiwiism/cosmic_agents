package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.bots.BotEntry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRuntimeRegistryTest {
    @Test
    void findsEntriesByCharacterIdAndName() {
        Character leader = character(100, "Leader");
        Character alpha = character(200, "Alpha");
        Character beta = character(201, "Beta");
        BotEntry alphaEntry = new BotEntry(alpha, leader, null);
        BotEntry betaEntry = new BotEntry(beta, leader, null);
        Map<Integer, List<BotEntry>> entries = new ConcurrentHashMap<>();
        entries.put(leader.getId(), new CopyOnWriteArrayList<>(List.of(alphaEntry, betaEntry)));

        assertSame(betaEntry, AgentRuntimeRegistry.findByCharacterId(entries, leader.getId(), beta.getId()));
        assertSame(alphaEntry, AgentRuntimeRegistry.findByName(entries, leader.getId(), "alpha"));
        assertSame(leader, AgentRuntimeRegistry.activeLeaderByAgentCharacterId(entries, alpha.getId()));
        assertSame(alpha, AgentRuntimeRegistry.firstAgent(entries, leader.getId()));
        assertSame(alphaEntry, AgentRuntimeRegistry.firstEntry(entries, leader.getId()));
        assertTrue(AgentRuntimeRegistry.isFirstEntryForLeader(entries, alphaEntry));
        assertFalse(AgentRuntimeRegistry.isFirstEntryForLeader(entries, betaEntry));
    }

    @Test
    void returnsNullOrEmptyForMissingEntries() {
        Map<Integer, List<BotEntry>> entries = new ConcurrentHashMap<>();

        assertNull(AgentRuntimeRegistry.findByCharacterId(entries, 100, 200));
        assertNull(AgentRuntimeRegistry.findByName(entries, 100, "Alpha"));
        assertNull(AgentRuntimeRegistry.findByName(entries, 100, null));
        assertNull(AgentRuntimeRegistry.activeLeaderByAgentCharacterId(entries, 200));
        assertNull(AgentRuntimeRegistry.firstAgent(entries, 100));
        assertNull(AgentRuntimeRegistry.firstEntry(entries, 100));
        assertFalse(AgentRuntimeRegistry.isFirstEntryForLeader(entries, null));
        assertEquals(List.of(), AgentRuntimeRegistry.entriesForLeader(entries, 100));
    }

    @Test
    void returnsDefensiveEntryCopy() {
        Character leader = character(100, "Leader");
        BotEntry entry = new BotEntry(character(200, "Alpha"), leader, null);
        Map<Integer, List<BotEntry>> entries = new ConcurrentHashMap<>();
        entries.put(leader.getId(), new CopyOnWriteArrayList<>(List.of(entry)));

        List<BotEntry> snapshot = AgentRuntimeRegistry.entriesForLeader(entries, leader.getId());

        assertEquals(List.of(entry), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(entry));
    }

    private static Character character(int id, String name) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getName()).thenReturn(name);
        return character;
    }
}
