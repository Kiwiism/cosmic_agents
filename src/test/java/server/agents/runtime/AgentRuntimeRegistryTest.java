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
        AgentRuntimeRegistry.clear();

        AgentRuntimeRegistry.registerEntry(leader.getId(), entry);

        assertSame(entry, AgentRuntimeRegistry.firstEntry(AgentRuntimeRegistry.entriesByLeaderId(), leader.getId()));
        assertSame(entry, AgentRuntimeRegistry.firstEntry(leader.getId()));
        assertSame(alpha, AgentRuntimeRegistry.firstAgent(leader.getId()));
        assertSame(entry, AgentRuntimeRegistry.findByCharacterId(leader.getId(), alpha.getId()));
        assertSame(entry, AgentRuntimeRegistry.findByName(leader.getId(), "alpha"));
        assertSame(leader, AgentRuntimeRegistry.activeLeaderByAgentCharacterId(alpha.getId()));
        assertTrue(AgentRuntimeRegistry.isFirstEntryForLeader(entry));
        assertEquals(List.of(entry), AgentRuntimeRegistry.entriesForLeader(leader.getId()));
        AgentRuntimeRegistry.clear();
    }

    @Test
    void countsAndListsActiveAgentCharactersFromLiveStore() {
        Character leader = character(100, "Leader");
        Character alpha = character(200, "Alpha");
        Character beta = character(201, "Beta");
        AgentRuntimeRegistry.clear();

        AgentRuntimeRegistry.registerEntry(leader.getId(), new AgentRuntimeEntry(alpha, leader, null));
        AgentRuntimeRegistry.registerEntry(leader.getId(), new AgentRuntimeEntry(null, leader, null));
        AgentRuntimeRegistry.registerEntry(leader.getId(), new AgentRuntimeEntry(beta, leader, null));

        assertEquals(3, AgentRuntimeRegistry.activeAgentCountForLeader(leader.getId()));
        assertEquals(List.of(alpha, beta), AgentRuntimeRegistry.activeAgentCharactersForLeader(leader.getId()));
        assertEquals(0, AgentRuntimeRegistry.activeAgentCountForLeader(999));
        assertEquals(List.of(), AgentRuntimeRegistry.activeAgentCharactersForLeader(999));

        AgentRuntimeRegistry.clear();
    }

    @Test
    void detectsOnlyUnclaimedBotClientCharacters() {
        Character leader = character(100, "Leader");
        Character agent = character(200, "Agent");
        Character player = character(201, "Player");
        when(agent.getClient()).thenReturn(mock(BotClient.class));
        when(player.getClient()).thenReturn(mock(Client.class));
        AgentRuntimeRegistry.clear();

        assertTrue(AgentRuntimeRegistry.isUnclaimedBotClientCharacter(agent));
        assertFalse(AgentRuntimeRegistry.isUnclaimedBotClientCharacter(player));
        assertFalse(AgentRuntimeRegistry.isUnclaimedBotClientCharacter(null));

        AgentRuntimeRegistry.registerEntry(leader.getId(), new AgentRuntimeEntry(agent, leader, null));

        assertFalse(AgentRuntimeRegistry.isUnclaimedBotClientCharacter(agent));

        AgentRuntimeRegistry.clear();
    }

    @Test
    void findsUnclaimedOnlineAgentByNameThroughLookup() {
        Character leader = character(100, "Leader");
        Character agent = character(200, "Agent");
        Character player = character(201, "Player");
        when(agent.getClient()).thenReturn(mock(BotClient.class));
        when(player.getClient()).thenReturn(mock(Client.class));
        AgentRuntimeRegistry.clear();

        assertSame(agent, AgentRuntimeRegistry.findUnclaimedOnlineAgentByName(
                "Agent", 0, (world, name) -> agent));
        assertNull(AgentRuntimeRegistry.findUnclaimedOnlineAgentByName(
                "Player", 0, (world, name) -> player));

        AgentRuntimeRegistry.registerEntry(leader.getId(), new AgentRuntimeEntry(agent, leader, null));

        assertNull(AgentRuntimeRegistry.findUnclaimedOnlineAgentByName(
                "Agent", 0, (world, name) -> agent));

        AgentRuntimeRegistry.clear();
    }

    @Test
    void replacementKeepsOnlyNewestGenerationInConstantTimeIndex() {
        Character leader = character(100, "Leader");
        Character originalAgent = character(200, "Agent");
        Character replacementAgent = character(200, "Agent");
        AgentRuntimeEntry original = new AgentRuntimeEntry(originalAgent, leader, null);
        AgentRuntimeEntry replacement = new AgentRuntimeEntry(replacementAgent, leader, null);
        AgentRuntimeRegistry.clear();

        AgentRuntimeRegistry.registerEntry(leader.getId(), original);
        AgentRuntimeRegistry.registerEntry(leader.getId(), replacement);

        assertFalse(AgentRuntimeRegistry.isActiveSession(original, original.sessionGeneration()));
        assertTrue(AgentRuntimeRegistry.isActiveSession(replacement, replacement.sessionGeneration()));
        assertSame(replacement, AgentRuntimeRegistry.findByAgentCharacterId(replacementAgent.getId()));
        assertEquals(leader.getId(), AgentRuntimeRegistry.leaderIdForAgentCharacter(replacementAgent.getId()));
        assertEquals(List.of(replacement), AgentRuntimeRegistry.entriesForLeader(leader.getId()));

        AgentRuntimeRegistry.unregisterEntry(leader.getId(), original);
        assertTrue(AgentRuntimeRegistry.isActiveSession(replacement, replacement.sessionGeneration()));

        AgentRuntimeRegistry.unregisterEntry(leader.getId(), replacement);
        assertFalse(AgentRuntimeRegistry.hasActiveAgentCharacterId(replacementAgent.getId()));
        AgentRuntimeRegistry.clear();
    }

    @Test
    void repeatedRegistrationIsIdempotentAndLeaderReplacementIsAtomic() {
        Character originalLeader = character(100, "OriginalLeader");
        Character replacementLeader = character(101, "ReplacementLeader");
        Character agent = character(200, "Agent");
        AgentRuntimeEntry original = new AgentRuntimeEntry(agent, originalLeader, null);
        AgentRuntimeEntry replacement = new AgentRuntimeEntry(agent, replacementLeader, null);
        AgentRuntimeRegistry.clear();

        AgentRuntimeRegistry.registerEntry(originalLeader.getId(), original);
        AgentRuntimeRegistry.registerEntry(originalLeader.getId(), original);

        assertEquals(List.of(original), AgentRuntimeRegistry.entriesForLeader(originalLeader.getId()));

        AgentRuntimeRegistry.registerEntry(replacementLeader.getId(), replacement);

        assertEquals(List.of(), AgentRuntimeRegistry.entriesForLeader(originalLeader.getId()));
        assertEquals(List.of(replacement), AgentRuntimeRegistry.entriesForLeader(replacementLeader.getId()));
        assertFalse(AgentRuntimeRegistry.isActiveSession(original, original.sessionGeneration()));
        assertTrue(AgentRuntimeRegistry.isActiveSession(replacement, replacement.sessionGeneration()));
        AgentRuntimeRegistry.clear();
    }

    private static Character character(int id, String name) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getName()).thenReturn(name);
        return character;
    }
}
