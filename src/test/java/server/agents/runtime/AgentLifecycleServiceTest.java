package server.agents.runtime;

import client.Character;
import client.BotClient;
import client.Client;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.auth.AgentAuthorizationResult;
import server.agents.auth.AgentOwnershipService;
import server.agents.integration.AgentBotManagerStatusRuntime;
import server.agents.registry.AgentResolvedCharacter;
import server.bots.BotEntry;
import server.maps.MapleMap;

import java.awt.Point;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentLifecycleServiceTest {
    @Test
    void registersAgentWithScheduledTickAndSpawnStatusCheck() {
        Character leader = character(100, "Leader");
        Character agent = character(200, "Alpha");
        ScheduledFuture<?> scheduledTask = mock(ScheduledFuture.class);
        AtomicReference<Runnable> scheduledTick = new AtomicReference<>();
        AtomicReference<BotEntry> tickedEntry = new AtomicReference<>();
        AtomicReference<BotEntry> normalizedEntry = new AtomicReference<>();
        AtomicBoolean normalized = new AtomicBoolean();
        AgentRuntimeRegistry.entriesByLeaderId().clear();
        AgentFormationService.formationsByLeaderId().clear();

        try (MockedStatic<AgentBotManagerStatusRuntime> status = mockStatic(AgentBotManagerStatusRuntime.class)) {
            BotEntry entry = AgentLifecycleService.registerAgent(
                    leader.getId(),
                    leader,
                    agent,
                    true,
                    new AgentLifecycleService.RegisterHooks(
                            50L,
                            (tick, periodMs) -> {
                                assertEquals(50L, periodMs);
                                scheduledTick.set(tick);
                                return scheduledTask;
                            },
                            (activeEntry, leaderCharId, agentCharId) -> {
                                tickedEntry.set(activeEntry);
                                assertEquals(leader.getId(), leaderCharId);
                                assertEquals(agent.getId(), agentCharId);
                            },
                            ignored -> {
                                throw new AssertionError("nothing should be replaced");
                            },
                            AgentFormationService.defaultStagger(60, 120),
                            entryToNormalize -> {
                                normalizedEntry.set(entryToNormalize);
                                normalized.set(true);
                            },
                            () -> 123L));

            assertEquals(List.of(entry), AgentRuntimeRegistry.entriesForLeader(leader.getId()));
            assertSame(entry, normalizedEntry.get());
            assertTrue(normalized.get());
            scheduledTick.get().run();
            assertSame(entry, tickedEntry.get());
            status.verify(() -> AgentBotManagerStatusRuntime.scheduleSpawnStatusCheck(entry, agent, 123L));
        } finally {
            AgentRuntimeRegistry.entriesByLeaderId().clear();
            AgentFormationService.formationsByLeaderId().clear();
        }
    }

    @Test
    void registerAgentReplacesSameCharacterAndCancelsOldTask() {
        Character leader = character(100, "Leader");
        Character agent = character(200, "Alpha");
        BotEntry oldEntry = new BotEntry(agent, leader, mock(ScheduledFuture.class));
        ScheduledFuture<?> newTask = mock(ScheduledFuture.class);
        AtomicInteger cancelled = new AtomicInteger();
        AgentRuntimeRegistry.entriesByLeaderId().clear();
        AgentRuntimeRegistry.mutableEntriesForLeader(leader.getId()).add(oldEntry);

        try (MockedStatic<AgentBotManagerStatusRuntime> status = mockStatic(AgentBotManagerStatusRuntime.class)) {
            BotEntry newEntry = AgentLifecycleService.registerAgent(
                    leader.getId(),
                    leader,
                    agent,
                    false,
                    new AgentLifecycleService.RegisterHooks(
                            50L,
                            (tick, periodMs) -> newTask,
                            (entry, leaderCharId, agentCharId) -> {},
                            entry -> {
                                assertSame(oldEntry, entry);
                                cancelled.incrementAndGet();
                            },
                            AgentFormationService.defaultStagger(60, 120),
                            entry -> {
                                throw new AssertionError("normalize should not run");
                            },
                            () -> 456L));

            assertEquals(1, cancelled.get());
            assertEquals(List.of(newEntry), AgentRuntimeRegistry.entriesForLeader(leader.getId()));
            status.verify(() -> AgentBotManagerStatusRuntime.scheduleSpawnStatusCheck(newEntry, agent, 456L));
        } finally {
            AgentRuntimeRegistry.entriesByLeaderId().clear();
        }
    }

    @Test
    void spawnsOnlineAgentWithLegacyHooks() throws SQLException {
        Character leader = character(100, "Leader");
        Character agent = character(200, "Alpha", new BotClient(0, 0));
        MapleMap map = mock(MapleMap.class);
        Point leaderPosition = new Point(10, 20);
        Point spawnPosition = new Point(11, 21);
        AgentOwnershipService ownership = mock(AgentOwnershipService.class);
        AtomicInteger registered = new AtomicInteger();
        AtomicInteger placed = new AtomicInteger();
        AtomicInteger followed = new AtomicInteger();
        AtomicInteger changedMap = new AtomicInteger();
        AgentRuntimeRegistry.entriesByLeaderId().clear();
        when(map.getId()).thenReturn(100000000);
        when(leader.getMap()).thenReturn(map);
        when(leader.getPosition()).thenReturn(leaderPosition);
        when(agent.getMapId()).thenReturn(999999999);
        when(ownership.resolveCharacterByName("Alpha")).thenReturn(new AgentResolvedCharacter(200, "Alpha", 1, agent));
        when(ownership.ensureCanControl(leader, new AgentResolvedCharacter(200, "Alpha", 1, agent)))
                .thenReturn(AgentAuthorizationResult.allowed(true));

        AgentLifecycleService.AgentSpawnResult result = AgentLifecycleService.spawnAgentForLeader(
                leader,
                "Alpha",
                ownership,
                new AgentLifecycleService.SpawnHooks(
                        (spawnMap, point) -> {
                            assertSame(map, spawnMap);
                            assertSame(leaderPosition, point);
                            return spawnPosition;
                        },
                        (leaderId, resolvedLeader, resolvedAgent) -> {
                            registered.incrementAndGet();
                            BotEntry entry = new BotEntry(resolvedAgent, resolvedLeader, null);
                            AgentRuntimeRegistry.mutableEntriesForLeader(leaderId).add(entry);
                            return entry;
                        },
                        (charId, world, channel, targetMap, desiredPosition) -> {
                            throw new AssertionError("online spawn should not load offline agent");
                        },
                        (entry, placedAgent, spawnMap, position) -> {
                            placed.incrementAndGet();
                            assertSame(agent, placedAgent);
                            assertSame(map, spawnMap);
                            assertSame(spawnPosition, position);
                        },
                        entry -> followed.incrementAndGet(),
                        (changedAgent, spawnMap, position) -> {
                            changedMap.incrementAndGet();
                            assertSame(agent, changedAgent);
                            assertSame(map, spawnMap);
                            assertSame(spawnPosition, position);
                        }));

        assertTrue(result.success());
        assertSame(agent, result.agent());
        assertTrue(result.autoRegistered());
        assertEquals(1, registered.get());
        assertEquals(1, placed.get());
        assertEquals(1, followed.get());
        assertEquals(1, changedMap.get());
        AgentRuntimeRegistry.entriesByLeaderId().clear();
    }

    @Test
    void failsWhenOnlineAgentIsControlledByAnotherLeader() throws SQLException {
        Character leader = character(100, "Leader");
        Character otherLeader = character(101, "Other");
        Character agent = character(200, "Alpha", new BotClient(0, 0));
        MapleMap map = mock(MapleMap.class);
        AgentOwnershipService ownership = mock(AgentOwnershipService.class);
        AgentRuntimeRegistry.entriesByLeaderId().clear();
        AgentRuntimeRegistry.mutableEntriesForLeader(otherLeader.getId()).add(new BotEntry(agent, otherLeader, null));
        when(map.getId()).thenReturn(100000000);
        when(leader.getMap()).thenReturn(map);
        when(leader.getPosition()).thenReturn(new Point(10, 20));
        when(ownership.resolveCharacterByName("Alpha")).thenReturn(new AgentResolvedCharacter(200, "Alpha", 1, agent));
        when(ownership.ensureCanControl(leader, new AgentResolvedCharacter(200, "Alpha", 1, agent)))
                .thenReturn(AgentAuthorizationResult.allowed(false));

        AgentLifecycleService.AgentSpawnResult result = AgentLifecycleService.spawnAgentForLeader(
                leader,
                "Alpha",
                ownership,
                noSideEffectSpawnHooks());

        assertFalse(result.success());
        assertEquals("Bot 'Alpha' is controlled by Other.", result.errorMessage());
        AgentRuntimeRegistry.entriesByLeaderId().clear();
    }

    @Test
    void spawnsOfflineAgentWithLegacyHooks() throws SQLException {
        Client client = mock(Client.class);
        Character leader = character(100, "Leader");
        Character agent = character(200, "Alpha");
        MapleMap map = mock(MapleMap.class);
        Point spawnPosition = new Point(11, 21);
        AgentOwnershipService ownership = mock(AgentOwnershipService.class);
        AtomicReference<BotEntry> registeredEntry = new AtomicReference<>();
        AtomicInteger followed = new AtomicInteger();
        when(leader.getClient()).thenReturn(client);
        when(client.getWorld()).thenReturn(1);
        when(client.getChannel()).thenReturn(2);
        when(leader.getMap()).thenReturn(map);
        when(leader.getPosition()).thenReturn(new Point(10, 20));
        when(ownership.resolveCharacterByName("Alpha")).thenReturn(new AgentResolvedCharacter(200, "Alpha", 1, null));
        when(ownership.ensureCanControl(leader, new AgentResolvedCharacter(200, "Alpha", 1, null)))
                .thenReturn(AgentAuthorizationResult.allowed(false));

        AgentLifecycleService.AgentSpawnResult result = AgentLifecycleService.spawnAgentForLeader(
                leader,
                "Alpha",
                ownership,
                new AgentLifecycleService.SpawnHooks(
                        (spawnMap, point) -> spawnPosition,
                        (leaderId, resolvedLeader, resolvedAgent) -> {
                            BotEntry entry = new BotEntry(resolvedAgent, resolvedLeader, null);
                            registeredEntry.set(entry);
                            return entry;
                        },
                        (charId, world, channel, targetMap, desiredPosition) -> {
                            assertEquals(200, charId);
                            assertEquals(1, world);
                            assertEquals(2, channel);
                            assertSame(map, targetMap);
                            assertSame(spawnPosition, desiredPosition);
                            return agent;
                        },
                        (entry, placedAgent, spawnMap, position) -> {
                            throw new AssertionError("offline spawn should not place online agent");
                        },
                        entry -> {
                            assertSame(registeredEntry.get(), entry);
                            followed.incrementAndGet();
                        },
                        (changedAgent, spawnMap, position) -> {
                            throw new AssertionError("offline spawn should not change online map");
                        }));

        assertTrue(result.success());
        assertSame(agent, result.agent());
        assertFalse(result.autoRegistered());
        assertEquals(1, followed.get());
    }

    @Test
    void returnsLegacySpawnFailures() throws SQLException {
        Character leader = character(100, "Leader");
        Character realPlayer = character(200, "Alpha", mock(Client.class));
        AgentOwnershipService ownership = mock(AgentOwnershipService.class);
        when(ownership.resolveCharacterByName("Missing")).thenReturn(null);
        when(ownership.resolveCharacterByName("Alpha")).thenReturn(new AgentResolvedCharacter(200, "Alpha", 1, realPlayer));

        assertEquals("No character named 'Missing' exists.",
                AgentLifecycleService.spawnAgentForLeader(leader, "Missing", ownership, noSideEffectSpawnHooks()).errorMessage());
        assertEquals("'Alpha' is currently being played by a real player.",
                AgentLifecycleService.spawnAgentForLeader(leader, "Alpha", ownership, noSideEffectSpawnHooks()).errorMessage());
    }

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
        return character(id, name, mock(Client.class));
    }

    private static Character character(int id, String name, Client client) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getName()).thenReturn(name);
        when(character.getClient()).thenReturn(client);
        return character;
    }

    private static AgentLifecycleService.SpawnHooks noSideEffectSpawnHooks() {
        return new AgentLifecycleService.SpawnHooks(
                (map, point) -> point,
                (leaderId, leader, agent) -> {
                    throw new AssertionError("should not register agent");
                },
                (charId, world, channel, targetMap, desiredPosition) -> {
                    throw new AssertionError("should not load offline agent");
                },
                (entry, agent, spawnMap, spawnPosition) -> {
                    throw new AssertionError("should not place agent");
                },
                entry -> {
                    throw new AssertionError("should not follow leader");
                },
                (agent, spawnMap, spawnPosition) -> {
                    throw new AssertionError("should not change map");
                });
    }
}
