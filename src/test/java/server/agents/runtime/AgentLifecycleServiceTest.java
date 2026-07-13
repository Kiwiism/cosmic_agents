package server.agents.runtime;

import server.agents.capabilities.movement.AgentFormationService;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import client.Character;
import client.BotClient;
import client.Client;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.auth.AgentAuthorizationResult;
import server.agents.auth.AgentOwnershipService;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.registry.AgentResolvedCharacter;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.scheduler.AgentScheduleHandle;
import server.agents.runtime.scheduler.AgentSchedulerMode;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentLifecycleServiceTest {
    @Test
    void registersAgentWithScheduledTickAndSpawnStatusCheck() {
        Character leader = character(100, "Leader");
        Character agent = character(200, "Alpha");
        ScheduledFuture<?> scheduledTask = mock(ScheduledFuture.class);
        AtomicReference<Runnable> scheduledTick = new AtomicReference<>();
        AtomicReference<AgentRuntimeEntry> tickedEntry = new AtomicReference<>();
        AtomicReference<AgentRuntimeEntry> normalizedEntry = new AtomicReference<>();
        AtomicBoolean normalized = new AtomicBoolean();
        AgentRuntimeRegistry.clear();
        AgentFormationService.formationsByLeaderId().clear();

        try (MockedStatic<AgentLifecycleStatusCoordinator> status = mockStatic(AgentLifecycleStatusCoordinator.class)) {
            AgentRuntimeEntry entry = AgentLifecycleService.registerAgent(
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
            status.verify(() -> AgentLifecycleStatusCoordinator.scheduleSpawnStatusCheck(entry, agent, 123L));
        } finally {
            AgentRuntimeRegistry.clear();
            AgentFormationService.formationsByLeaderId().clear();
        }
    }

    @Test
    void immediateTickSeesInitializedPublishedEntry() {
        Character leader = character(100, "Leader");
        Character agent = character(200, "Alpha");
        ScheduledFuture<?> scheduledTask = mock(ScheduledFuture.class);
        AtomicReference<AgentRuntimeEntry> tickedEntry = new AtomicReference<>();
        AgentRuntimeRegistry.clear();

        try (MockedStatic<AgentLifecycleStatusCoordinator> status = mockStatic(AgentLifecycleStatusCoordinator.class)) {
            AgentRuntimeEntry entry = AgentLifecycleService.registerAgent(
                    leader.getId(), leader, agent, false,
                    new AgentLifecycleService.RegisterHooks(
                            50L,
                            (tick, periodMs) -> {
                                tick.run();
                                return scheduledTask;
                            },
                            (activeEntry, leaderCharId, agentCharId) -> {
                                assertEquals(List.of(activeEntry),
                                        AgentRuntimeRegistry.entriesForLeader(leaderCharId));
                                assertNotNull(AgentMovementStateRuntime.movementProfile(activeEntry));
                                tickedEntry.set(activeEntry);
                            },
                            ignored -> {},
                            AgentFormationService.defaultStagger(60, 120),
                            ignored -> {},
                            () -> 123L));

            assertSame(entry, tickedEntry.get());
            AgentScheduleHandle handle = (AgentScheduleHandle) entry.scheduledTaskState().task();
            assertEquals(AgentSchedulerMode.LEGACY_PER_AGENT, handle.mode());
            assertEquals(entry.sessionGeneration(), handle.sessionId().generation());
        } finally {
            AgentRuntimeRegistry.clear();
        }
    }

    @Test
    void schedulingFailureRollsBackRegistryPublication() {
        Character leader = character(100, "Leader");
        Character agent = character(200, "Alpha");
        AgentRuntimeRegistry.clear();

        try (MockedStatic<AgentLifecycleStatusCoordinator> status = mockStatic(AgentLifecycleStatusCoordinator.class)) {
            assertThrows(IllegalStateException.class, () -> AgentLifecycleService.registerAgent(
                    leader.getId(), leader, agent, false,
                    new AgentLifecycleService.RegisterHooks(
                            50L,
                            (tick, periodMs) -> {
                                throw new IllegalStateException("scheduler unavailable");
                            },
                            (entry, leaderCharId, agentCharId) -> {},
                            ignored -> {},
                            AgentFormationService.defaultStagger(60, 120),
                            ignored -> {},
                            () -> 123L)));

            assertTrue(AgentRuntimeRegistry.entriesForLeader(leader.getId()).isEmpty());
            status.verifyNoInteractions();
        } finally {
            AgentRuntimeRegistry.clear();
        }
    }

    @Test
    void registerAgentReplacesSameCharacterAndCancelsOldTask() {
        Character leader = character(100, "Leader");
        Character agent = character(200, "Alpha");
        AgentRuntimeEntry oldEntry = new AgentRuntimeEntry(agent, leader, mock(ScheduledFuture.class));
        ScheduledFuture<?> newTask = mock(ScheduledFuture.class);
        AtomicInteger cancelled = new AtomicInteger();
        AgentRuntimeRegistry.clear();
        AgentRuntimeRegistry.registerEntry(leader.getId(), oldEntry);

        try (MockedStatic<AgentLifecycleStatusCoordinator> status = mockStatic(AgentLifecycleStatusCoordinator.class)) {
            AgentRuntimeEntry newEntry = AgentLifecycleService.registerAgent(
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
            status.verify(() -> AgentLifecycleStatusCoordinator.scheduleSpawnStatusCheck(newEntry, agent, 456L));
        } finally {
            AgentRuntimeRegistry.clear();
        }
    }

    @Test
    void reloginAgentSkipsWhenLeaderIsOffline() throws SQLException {
        boolean relogged = AgentLifecycleService.reloginAgent(
                200,
                100,
                1,
                2,
                new AgentLifecycleService.ReloginHooks(
                        (world, leaderCharId) -> null,
                        (map, point) -> {
                            throw new AssertionError("should not resolve spawn position");
                        },
                        (charId, world, channel, targetMap, desiredPosition) -> {
                            throw new AssertionError("should not load agent");
                        },
                        (leaderId, leader, agent) -> {
                            throw new AssertionError("should not register agent");
                        },
                        (entry, delayMs, action) -> {
                            throw new AssertionError("should not schedule announcement");
                        },
                        () -> 1000L,
                        (agent, text) -> {
                            throw new AssertionError("should not speak");
                        }));

        assertFalse(relogged);
    }

    @Test
    void reloginAgentLoadsRegistersAndSchedulesReturnAnnouncement() throws SQLException {
        Character leader = character(100, "Leader");
        Character agent = character(200, "Alpha");
        MapleMap map = mock(MapleMap.class);
        Point leaderPosition = new Point(10, 20);
        Point spawnPosition = new Point(11, 21);
        AtomicReference<Runnable> delayedAction = new AtomicReference<>();
        AtomicReference<AgentRuntimeEntry> registeredEntry = new AtomicReference<>();
        AtomicReference<String> spoken = new AtomicReference<>();
        when(leader.getMap()).thenReturn(map);
        when(leader.getPosition()).thenReturn(leaderPosition);

        boolean relogged = AgentLifecycleService.reloginAgent(
                agent.getId(),
                leader.getId(),
                1,
                2,
                new AgentLifecycleService.ReloginHooks(
                        (world, leaderCharId) -> {
                            assertEquals(1, world);
                            assertEquals(leader.getId(), leaderCharId);
                            return leader;
                        },
                        (spawnMap, point) -> {
                            assertSame(map, spawnMap);
                            assertSame(leaderPosition, point);
                            return spawnPosition;
                        },
                        (charId, world, channel, targetMap, desiredPosition) -> {
                            assertEquals(agent.getId(), charId);
                            assertEquals(1, world);
                            assertEquals(2, channel);
                            assertSame(map, targetMap);
                            assertSame(spawnPosition, desiredPosition);
                            return agent;
                        },
                        (leaderId, resolvedLeader, resolvedAgent) -> {
                            AgentRuntimeEntry entry = new AgentRuntimeEntry(resolvedAgent, resolvedLeader, null);
                            registeredEntry.set(entry);
                            return entry;
                        },
                        (entry, delayMs, action) -> {
                            assertSame(registeredEntry.get(), entry);
                            assertEquals(1000L, delayMs);
                            delayedAction.set(action);
                        },
                        () -> 1000L,
                        (speakingAgent, text) -> {
                            assertSame(agent, speakingAgent);
                            spoken.set(text);
                        }));

        assertTrue(relogged);
        assertSame(agent, AgentRuntimeIdentityRuntime.bot(registeredEntry.get()));
        delayedAction.get().run();
        assertEquals("back!!", spoken.get());
    }

    @Test
    void reloginAgentQuietlyLogsSqlFailureAndReturnsFalse() {
        SQLException failure = new SQLException("load failed");
        AtomicInteger loggedAgentId = new AtomicInteger();
        AtomicReference<SQLException> loggedFailure = new AtomicReference<>();

        boolean relogged = AgentLifecycleService.reloginAgentQuietly(
                200,
                100,
                1,
                2,
                new AgentLifecycleService.ReloginHooks(
                        (world, leaderCharId) -> character(100, "Leader"),
                        (map, point) -> new Point(1, 2),
                        (charId, world, channel, targetMap, desiredPosition) -> {
                            throw failure;
                        },
                        (leaderId, leader, agent) -> {
                            throw new AssertionError("should not register after load failure");
                        },
                        (entry, delayMs, action) -> {
                            throw new AssertionError("should not schedule announcement");
                        },
                        () -> 1000L,
                        (agent, text) -> {
                            throw new AssertionError("should not speak");
                        }),
                (agentCharId, exception) -> {
                    loggedAgentId.set(agentCharId);
                    loggedFailure.set(exception);
                });

        assertFalse(relogged);
        assertEquals(200, loggedAgentId.get());
        assertSame(failure, loggedFailure.get());
    }

    @Test
    void dismissAgentByNameReturnsFalseWhenMissing() {
        AgentRuntimeRegistry.clear();

        boolean dismissed = AgentLifecycleService.dismissAgentByName(
                100,
                "Alpha",
                noSideEffectDismissHooks());

        assertFalse(dismissed);
    }

    @Test
    void dismissAgentByNameRemovesEntryCancelsStopsAndSchedulesFarewell() {
        Character leader = character(100, "Leader");
        Character agent = character(200, "Alpha");
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, null);
        AtomicReference<Runnable> delayedAction = new AtomicReference<>();
        AtomicInteger cancelled = new AtomicInteger();
        AtomicInteger stopped = new AtomicInteger();
        AtomicReference<String> said = new AtomicReference<>();
        AgentRuntimeRegistry.clear();
        AgentRuntimeRegistry.registerEntry(leader.getId(), entry);

        boolean dismissed = AgentLifecycleService.dismissAgentByName(
                leader.getId(),
                "alpha",
                new AgentLifecycleService.DismissHooks(
                        cancelledEntry -> {
                            assertSame(entry, cancelledEntry);
                            cancelled.incrementAndGet();
                        },
                        stoppedEntry -> {
                            assertSame(entry, stoppedEntry);
                            stopped.incrementAndGet();
                        },
                        (delayMs, action) -> {
                            assertEquals(500L, delayMs);
                            delayedAction.set(action);
                        },
                        () -> 500L,
                        (speakingEntry, text) -> {
                            assertSame(entry, speakingEntry);
                            said.set(text);
                        },
                        () -> "ok"));

        assertTrue(dismissed);
        assertEquals(List.of(), AgentRuntimeRegistry.entriesForLeader(leader.getId()));
        assertEquals(1, cancelled.get());
        assertEquals(1, stopped.get());
        delayedAction.get().run();
        assertEquals("ok", said.get());
        AgentRuntimeRegistry.clear();
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
        AgentRuntimeRegistry.clear();
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
                            AgentRuntimeEntry entry = new AgentRuntimeEntry(resolvedAgent, resolvedLeader, null);
                            AgentRuntimeRegistry.registerEntry(leaderId, entry);
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
        AgentRuntimeRegistry.clear();
    }

    @Test
    void failsWhenOnlineAgentIsControlledByAnotherLeader() throws SQLException {
        Character leader = character(100, "Leader");
        Character otherLeader = character(101, "Other");
        Character agent = character(200, "Alpha", new BotClient(0, 0));
        MapleMap map = mock(MapleMap.class);
        AgentOwnershipService ownership = mock(AgentOwnershipService.class);
        AgentRuntimeRegistry.clear();
        AgentRuntimeRegistry.registerEntry(
                otherLeader.getId(), new AgentRuntimeEntry(agent, otherLeader, null));
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
        AgentRuntimeRegistry.clear();
    }

    @Test
    void spawnsOfflineAgentWithLegacyHooks() throws SQLException {
        Client client = mock(Client.class);
        Character leader = character(100, "Leader");
        Character agent = character(200, "Alpha");
        MapleMap map = mock(MapleMap.class);
        Point spawnPosition = new Point(11, 21);
        AgentOwnershipService ownership = mock(AgentOwnershipService.class);
        AtomicReference<AgentRuntimeEntry> registeredEntry = new AtomicReference<>();
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
                            AgentRuntimeEntry entry = new AgentRuntimeEntry(resolvedAgent, resolvedLeader, null);
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
    void spawnAgentForLeaderQuietlyLogsSqlFailureAndReturnsLegacyMessage() {
        Client client = mock(Client.class);
        Character leader = character(100, "Leader");
        MapleMap map = mock(MapleMap.class);
        Point spawnPosition = new Point(11, 21);
        SQLException failure = new SQLException("load failed");
        AgentOwnershipService ownership = mock(AgentOwnershipService.class);
        AtomicReference<String> loggedAgentName = new AtomicReference<>();
        AtomicReference<Character> loggedLeader = new AtomicReference<>();
        AtomicReference<SQLException> loggedFailure = new AtomicReference<>();
        when(leader.getClient()).thenReturn(client);
        when(client.getWorld()).thenReturn(1);
        when(client.getChannel()).thenReturn(2);
        when(leader.getMap()).thenReturn(map);
        when(leader.getPosition()).thenReturn(new Point(10, 20));
        when(ownership.resolveCharacterByName("Alpha")).thenReturn(new AgentResolvedCharacter(200, "Alpha", 1, null));
        when(ownership.ensureCanControl(leader, new AgentResolvedCharacter(200, "Alpha", 1, null)))
                .thenReturn(AgentAuthorizationResult.allowed(false));

        AgentLifecycleService.AgentSpawnResult result = AgentLifecycleService.spawnAgentForLeaderQuietly(
                leader,
                "Alpha",
                ownership,
                new AgentLifecycleService.SpawnHooks(
                        (spawnMap, point) -> spawnPosition,
                        (leaderId, resolvedLeader, resolvedAgent) -> {
                            throw new AssertionError("should not register after load failure");
                        },
                        (charId, world, channel, targetMap, desiredPosition) -> {
                            throw failure;
                        },
                        (entry, placedAgent, spawnMap, position) -> {
                            throw new AssertionError("offline spawn should not place online agent");
                        },
                        entry -> {
                            throw new AssertionError("should not follow after load failure");
                        },
                        (changedAgent, spawnMap, position) -> {
                            throw new AssertionError("offline spawn should not change online map");
                        }),
                (agentName, failedLeader, exception) -> {
                    loggedAgentName.set(agentName);
                    loggedLeader.set(failedLeader);
                    loggedFailure.set(exception);
                });

        assertFalse(result.success());
        assertEquals("Failed to load bot character 'Alpha'.", result.errorMessage());
        assertEquals("Alpha", loggedAgentName.get());
        assertSame(leader, loggedLeader.get());
        assertSame(failure, loggedFailure.get());
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
        AgentRuntimeEntry first = new AgentRuntimeEntry(character(200, "Alpha"), leader, null);
        AgentRuntimeEntry second = new AgentRuntimeEntry(character(201, "Beta"), leader, null);
        Map<Integer, List<AgentRuntimeEntry>> entries = new ConcurrentHashMap<>();
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
    void cancelsScheduledTickOnlyWhenEntryHasTask() {
        Character leader = character(100, "Leader");
        ScheduledFuture<?> scheduledTask = mock(ScheduledFuture.class);

        AgentLifecycleService.cancelScheduledTickIfPresent(null);
        AgentLifecycleService.cancelScheduledTickIfPresent(new AgentRuntimeEntry(character(200, "NoTask"), leader, null));
        AgentLifecycleService.cancelScheduledTickIfPresent(new AgentRuntimeEntry(character(201, "Task"), leader, scheduledTask));

        verify(scheduledTask).cancel(false);
    }

    @Test
    void removesAgentByCharacterIdAndKeepsLeaderWhenEntriesRemain() {
        Character leader = character(100, "Leader");
        AgentRuntimeEntry first = new AgentRuntimeEntry(character(200, "Alpha"), leader, null);
        AgentRuntimeEntry second = new AgentRuntimeEntry(character(201, "Beta"), leader, null);
        Map<Integer, List<AgentRuntimeEntry>> entries = new ConcurrentHashMap<>();
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
        AgentRuntimeEntry only = new AgentRuntimeEntry(character(200, "Alpha"), leader, null);
        Map<Integer, List<AgentRuntimeEntry>> entries = new ConcurrentHashMap<>();
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
        Map<Integer, List<AgentRuntimeEntry>> entries = new ConcurrentHashMap<>();

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

    private static AgentLifecycleService.DismissHooks noSideEffectDismissHooks() {
        return new AgentLifecycleService.DismissHooks(
                entry -> {
                    throw new AssertionError("should not cancel task");
                },
                entry -> {
                    throw new AssertionError("should not stop agent");
                },
                (delayMs, action) -> {
                    throw new AssertionError("should not schedule farewell");
                },
                () -> 500L,
                (entry, text) -> {
                    throw new AssertionError("should not speak");
                },
                () -> "ok");
    }
}
