package server.agents.runtime;

import server.agents.capabilities.combat.AgentDeathStateRuntime;
import server.agents.capabilities.combat.AgentDeathTickService;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.dialogue.AgentEmote;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentDeathTickServiceTest {
    @Test
    void respawnNearLeaderRestoresHpTeleportsAndAnnounces() {
        MapleMap map = mock(MapleMap.class);
        Character leader = character(100, 1, map, new Point(50, 100));
        Character agent = character(200, 1, map, new Point(10, 100));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, null);
        Point ground = new Point(50, 99);
        AtomicInteger mapChanges = new AtomicInteger();
        AtomicReference<Point> groundedFrom = new AtomicReference<>();
        AtomicReference<Point> teleportedTo = new AtomicReference<>();
        AtomicInteger resets = new AtomicInteger();
        AtomicInteger broadcasts = new AtomicInteger();
        AtomicReference<String> spoken = new AtomicReference<>();
        AgentDeathStateRuntime.enterDeadState(entry, 1_000L, 500L);
        when(agent.getMaxHp()).thenReturn(123);

        AgentDeathTickService.respawnNearLeader(
                entry,
                agent,
                leader,
                new AgentDeathTickService.RespawnHooks(
                        (changedAgent, leaderMap, leaderPosition) -> mapChanges.incrementAndGet(),
                        (targetMap, point) -> {
                            assertSame(map, targetMap);
                            groundedFrom.set(point);
                            return ground;
                        },
                        (teleportEntry, teleportAgent, point) -> {
                            assertSame(entry, teleportEntry);
                            assertSame(agent, teleportAgent);
                            teleportedTo.set(point);
                        },
                        (resetEntry, resetAgent) -> {
                            assertSame(entry, resetEntry);
                            assertSame(agent, resetAgent);
                            resets.incrementAndGet();
                        },
                        (broadcastEntry, broadcastAgent) -> {
                            assertSame(entry, broadcastEntry);
                            assertSame(agent, broadcastAgent);
                            broadcasts.incrementAndGet();
                        },
                        (speakingAgent, text) -> {
                            assertSame(agent, speakingAgent);
                            spoken.set(text);
                        }));

        assertFalse(AgentDeathStateRuntime.isDead(entry));
        verify(agent).updateHp(123);
        assertEquals(0, mapChanges.get());
        assertEquals(new Point(50, 99), groundedFrom.get());
        assertSame(ground, teleportedTo.get());
        assertEquals(1, resets.get());
        assertEquals(1, broadcasts.get());
        assertEquals("back!", spoken.get());
        verify(agent).changeFaceExpression(AgentEmote.GLARE.getValue());
    }

    @Test
    void respawnNearLeaderChangesMapWhenAgentIsElsewhereAndFallsBackToLeaderPosition() {
        MapleMap leaderMap = mock(MapleMap.class);
        MapleMap agentMap = mock(MapleMap.class);
        Point leaderPosition = new Point(50, 100);
        Character leader = character(100, 1, leaderMap, leaderPosition);
        Character agent = character(200, 2, agentMap, new Point(10, 100));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, null);
        AtomicInteger mapChanges = new AtomicInteger();
        AtomicReference<Point> teleportedTo = new AtomicReference<>();
        when(agent.getMaxHp()).thenReturn(123);

        AgentDeathTickService.respawnNearLeader(
                entry,
                agent,
                leader,
                new AgentDeathTickService.RespawnHooks(
                        (changedAgent, targetMap, targetPosition) -> {
                            assertSame(agent, changedAgent);
                            assertSame(leaderMap, targetMap);
                            assertSame(leaderPosition, targetPosition);
                            mapChanges.incrementAndGet();
                        },
                        (targetMap, point) -> null,
                        (teleportEntry, teleportAgent, point) -> teleportedTo.set(point),
                        (resetEntry, resetAgent) -> {},
                        (broadcastEntry, broadcastAgent) -> {},
                        (speakingAgent, text) -> {}));

        assertEquals(1, mapChanges.get());
        assertSame(leaderPosition, teleportedTo.get());
    }

    @Test
    void returnsFalseWhenAgentIsAliveAndDoesNotNeedDeadState() {
        AgentRuntimeEntry entry = entry();
        Counters counters = new Counters();

        boolean consumed = AgentDeathTickService.handleDeadTick(
                entry, agent(entry), falseCondition(), counters::enterDead, counters::respawn, 1_000L);

        assertFalse(consumed);
        counters.assertCounts(0, 0);
    }

    @Test
    void entersDeadStateAndConsumesTick() {
        AgentRuntimeEntry entry = entry();
        Counters counters = new Counters();

        boolean consumed = AgentDeathTickService.handleDeadTick(
                entry, agent(entry), trueCondition(), counters::enterDead, counters::respawn, 1_000L);

        assertTrue(consumed);
        counters.assertCounts(1, 0);
        assertTrue(AgentDeathStateRuntime.isDead(entry));
    }

    @Test
    void consumesTickWhileWaitingForRespawn() {
        AgentRuntimeEntry entry = entry();
        AgentDeathStateRuntime.enterDeadState(entry, 1_000L, 500L);
        Counters counters = new Counters();

        boolean consumed = AgentDeathTickService.handleDeadTick(
                entry, agent(entry), falseCondition(), counters::enterDead, counters::respawn, 1_100L);

        assertTrue(consumed);
        counters.assertCounts(0, 0);
    }

    @Test
    void runsRespawnWhenDeadWindowIsDue() {
        AgentRuntimeEntry entry = entry();
        AgentDeathStateRuntime.enterDeadState(entry, 1_000L, 500L);
        Counters counters = new Counters();

        boolean consumed = AgentDeathTickService.handleDeadTick(
                entry, agent(entry), falseCondition(), counters::enterDead, counters::respawn, 1_500L);

        assertTrue(consumed);
        counters.assertCounts(0, 1);
    }

    private static java.util.function.BooleanSupplier falseCondition() {
        return () -> false;
    }

    private static java.util.function.BooleanSupplier trueCondition() {
        return () -> true;
    }

    private static AgentRuntimeEntry entry() {
        return new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
    }

    private static Character character(int id, int mapId, MapleMap map, Point position) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getMapId()).thenReturn(mapId);
        when(character.getMap()).thenReturn(map);
        when(character.getPosition()).thenReturn(position);
        return character;
    }

    private static Character agent(AgentRuntimeEntry entry) {
        return entry.bot();
    }

    private static final class Counters {
        private final AtomicInteger deadEntries = new AtomicInteger();
        private final AtomicInteger respawns = new AtomicInteger();

        private void enterDead(AgentRuntimeEntry entry, Character agent) {
            deadEntries.incrementAndGet();
            AgentDeathStateRuntime.enterDeadState(entry, 1_000L, 500L);
        }

        private void respawn() {
            respawns.incrementAndGet();
        }

        private void assertCounts(int expectedDeadEntries, int expectedRespawns) {
            assertEquals(expectedDeadEntries, deadEntries.get());
            assertEquals(expectedRespawns, respawns.get());
        }
    }
}
