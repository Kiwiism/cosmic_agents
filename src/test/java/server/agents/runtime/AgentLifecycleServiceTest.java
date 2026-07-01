package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.bots.BotEntry;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLifecycleServiceTest {
    @Test
    void removesAllLeaderEntriesAndAssociatedState() {
        Character leader = character(100, "Leader");
        BotEntry first = new BotEntry(character(200, "Alpha"), leader, null);
        BotEntry second = new BotEntry(character(201, "Beta"), leader, null);
        Map<Integer, List<BotEntry>> entries = new ConcurrentHashMap<>();
        Map<Integer, Object> formations = new ConcurrentHashMap<>();
        Map<Integer, Point> townAnchors = new ConcurrentHashMap<>();
        entries.put(leader.getId(), new CopyOnWriteArrayList<>(List.of(first, second)));
        formations.put(leader.getId(), new Object());
        townAnchors.put(leader.getId(), new Point(1, 2));
        AtomicInteger cancelled = new AtomicInteger();

        AgentLifecycleService.removeLeaderEntries(
                entries, formations, townAnchors, leader.getId(), ignored -> cancelled.incrementAndGet());

        assertFalse(entries.containsKey(leader.getId()));
        assertFalse(formations.containsKey(leader.getId()));
        assertFalse(townAnchors.containsKey(leader.getId()));
        assertEquals(2, cancelled.get());
    }

    @Test
    void removesAgentByCharacterIdAndKeepsLeaderWhenEntriesRemain() {
        Character leader = character(100, "Leader");
        BotEntry first = new BotEntry(character(200, "Alpha"), leader, null);
        BotEntry second = new BotEntry(character(201, "Beta"), leader, null);
        Map<Integer, List<BotEntry>> entries = new ConcurrentHashMap<>();
        Map<Integer, Object> formations = new ConcurrentHashMap<>();
        Map<Integer, Point> townAnchors = new ConcurrentHashMap<>();
        entries.put(leader.getId(), new CopyOnWriteArrayList<>(List.of(first, second)));
        formations.put(leader.getId(), new Object());
        townAnchors.put(leader.getId(), new Point(1, 2));
        AtomicInteger cancelled = new AtomicInteger();

        boolean removed = AgentLifecycleService.removeAgentByCharacterId(
                entries, formations, townAnchors, 200, ignored -> cancelled.incrementAndGet());

        assertTrue(removed);
        assertEquals(List.of(second), entries.get(leader.getId()));
        assertTrue(formations.containsKey(leader.getId()));
        assertTrue(townAnchors.containsKey(leader.getId()));
        assertEquals(1, cancelled.get());
    }

    @Test
    void removesEmptyLeaderStateAfterLastAgentRemoval() {
        Character leader = character(100, "Leader");
        BotEntry only = new BotEntry(character(200, "Alpha"), leader, null);
        Map<Integer, List<BotEntry>> entries = new ConcurrentHashMap<>();
        Map<Integer, Object> formations = new ConcurrentHashMap<>();
        Map<Integer, Point> townAnchors = new ConcurrentHashMap<>();
        entries.put(leader.getId(), new CopyOnWriteArrayList<>(List.of(only)));
        formations.put(leader.getId(), new Object());
        townAnchors.put(leader.getId(), new Point(1, 2));

        boolean removed = AgentLifecycleService.removeAgentByCharacterId(
                entries, formations, townAnchors, 200, ignored -> {});

        assertTrue(removed);
        assertFalse(entries.containsKey(leader.getId()));
        assertFalse(formations.containsKey(leader.getId()));
        assertFalse(townAnchors.containsKey(leader.getId()));
    }

    @Test
    void returnsFalseWhenAgentIsMissing() {
        Map<Integer, List<BotEntry>> entries = new ConcurrentHashMap<>();

        assertFalse(AgentLifecycleService.removeAgentByCharacterId(
                entries, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), 404, ignored -> {}));
    }

    private static Character character(int id, String name) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getName()).thenReturn(name);
        return character;
    }
}
