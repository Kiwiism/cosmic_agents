package server.agents.runtime;

import client.BotClient;
import client.Character;
import client.Client;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

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
        AgentRuntimeEntry alphaEntry = new AgentRuntimeEntry(alpha, leader, null);
        AgentRuntimeEntry betaEntry = new AgentRuntimeEntry(beta, leader, null);
        Map<Integer, List<AgentRuntimeEntry>> entries = new ConcurrentHashMap<>();
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
        Map<Integer, List<AgentRuntimeEntry>> entries = new ConcurrentHashMap<>();

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
        AgentRuntimeEntry entry = new AgentRuntimeEntry(character(200, "Alpha"), leader, null);
        Map<Integer, List<AgentRuntimeEntry>> entries = new ConcurrentHashMap<>();
        entries.put(leader.getId(), new CopyOnWriteArrayList<>(List.of(entry)));

        List<AgentRuntimeEntry> snapshot = AgentRuntimeRegistry.entriesForLeader(entries, leader.getId());

        assertEquals(List.of(entry), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(entry));
    }

    @Test
    void returnsDefensiveAgentEntryReadView() {
        Character leader = character(100, "Leader");
        AgentRuntimeEntry entry = new AgentRuntimeEntry(character(200, "Alpha"), leader, null);
        Map<Integer, List<AgentRuntimeEntry>> entries = new ConcurrentHashMap<>();
        entries.put(leader.getId(), new CopyOnWriteArrayList<>(List.of(entry)));

        List<AgentRuntimeEntry> snapshot = AgentRuntimeRegistry.agentEntriesForLeader(entries, leader.getId());

        assertEquals(List.of(entry), snapshot);
        assertThrows(UnsupportedOperationException.class, () ->
                snapshot.add(new AgentRuntimeEntry(character(201, "Beta"), leader, null)));
    }

    @Test
    void ownsMutableLiveEntryStore() {
        Character leader = character(100, "Leader");
        Character alpha = character(200, "Alpha");
        AgentRuntimeEntry entry = new AgentRuntimeEntry(alpha, leader, null);
        AgentRuntimeRegistry.entriesByLeaderId().clear();

        AgentRuntimeRegistry.mutableEntriesForLeader(leader.getId()).add(entry);

        assertSame(entry, AgentRuntimeRegistry.firstEntry(AgentRuntimeRegistry.entriesByLeaderId(), leader.getId()));
        assertSame(entry, AgentRuntimeRegistry.firstEntry(leader.getId()));
        assertSame(alpha, AgentRuntimeRegistry.firstAgent(leader.getId()));
        assertSame(entry, AgentRuntimeRegistry.findByCharacterId(leader.getId(), alpha.getId()));
        assertSame(entry, AgentRuntimeRegistry.findByName(leader.getId(), "alpha"));
        assertSame(leader, AgentRuntimeRegistry.activeLeaderByAgentCharacterId(alpha.getId()));
        assertTrue(AgentRuntimeRegistry.isFirstEntryForLeader(entry));
        assertEquals(List.of(entry), AgentRuntimeRegistry.entriesForLeader(leader.getId()));
        AgentRuntimeRegistry.entriesByLeaderId().clear();
    }

    @Test
    void countsAndListsActiveAgentCharactersFromLiveStore() {
        Character leader = character(100, "Leader");
        Character alpha = character(200, "Alpha");
        Character beta = character(201, "Beta");
        AgentRuntimeRegistry.entriesByLeaderId().clear();

        AgentRuntimeRegistry.mutableEntriesForLeader(leader.getId()).add(new AgentRuntimeEntry(alpha, leader, null));
        AgentRuntimeRegistry.mutableEntriesForLeader(leader.getId()).add(new AgentRuntimeEntry(null, leader, null));
        AgentRuntimeRegistry.mutableEntriesForLeader(leader.getId()).add(new AgentRuntimeEntry(beta, leader, null));

        assertEquals(3, AgentRuntimeRegistry.activeAgentCountForLeader(leader.getId()));
        assertEquals(List.of(alpha, beta), AgentRuntimeRegistry.activeAgentCharactersForLeader(leader.getId()));
        assertEquals(0, AgentRuntimeRegistry.activeAgentCountForLeader(999));
        assertEquals(List.of(), AgentRuntimeRegistry.activeAgentCharactersForLeader(999));

        AgentRuntimeRegistry.entriesByLeaderId().clear();
    }

    @Test
    void detectsOnlyUnclaimedBotClientCharacters() {
        Character leader = character(100, "Leader");
        Character agent = character(200, "Agent");
        Character player = character(201, "Player");
        when(agent.getClient()).thenReturn(mock(BotClient.class));
        when(player.getClient()).thenReturn(mock(Client.class));
        AgentRuntimeRegistry.entriesByLeaderId().clear();

        assertTrue(AgentRuntimeRegistry.isUnclaimedBotClientCharacter(agent));
        assertFalse(AgentRuntimeRegistry.isUnclaimedBotClientCharacter(player));
        assertFalse(AgentRuntimeRegistry.isUnclaimedBotClientCharacter(null));

        AgentRuntimeRegistry.mutableEntriesForLeader(leader.getId()).add(new AgentRuntimeEntry(agent, leader, null));

        assertFalse(AgentRuntimeRegistry.isUnclaimedBotClientCharacter(agent));

        AgentRuntimeRegistry.entriesByLeaderId().clear();
    }

    @Test
    void findsUnclaimedOnlineAgentByNameThroughLookup() {
        Character leader = character(100, "Leader");
        Character agent = character(200, "Agent");
        Character player = character(201, "Player");
        when(agent.getClient()).thenReturn(mock(BotClient.class));
        when(player.getClient()).thenReturn(mock(Client.class));
        AgentRuntimeRegistry.entriesByLeaderId().clear();

        assertSame(agent, AgentRuntimeRegistry.findUnclaimedOnlineAgentByName(
                "Agent", 0, (world, name) -> agent));
        assertNull(AgentRuntimeRegistry.findUnclaimedOnlineAgentByName(
                "Player", 0, (world, name) -> player));

        AgentRuntimeRegistry.mutableEntriesForLeader(leader.getId()).add(new AgentRuntimeEntry(agent, leader, null));

        assertNull(AgentRuntimeRegistry.findUnclaimedOnlineAgentByName(
                "Agent", 0, (world, name) -> agent));

        AgentRuntimeRegistry.entriesByLeaderId().clear();
    }

    private static Character character(int id, String name) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getName()).thenReturn(name);
        return character;
    }
}
