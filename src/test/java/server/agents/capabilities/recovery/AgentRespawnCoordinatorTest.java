package server.agents.capabilities.recovery;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.combat.AgentDeathStateRuntime;
import server.agents.capabilities.dialogue.AgentEmote;
import server.agents.integration.AgentRelationshipRuntime;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyInt;

class AgentRespawnCoordinatorTest {
    @Test
    void normalDeathUsesReturnMapAndFullyHealsOnlyInTown() {
        MapleMap deathMap = map(100, false);
        MapleMap town = map(200, true);
        when(deathMap.getReturnMap()).thenReturn(town);
        AgentFixture fixture = fixture(deathMap);
        AgentDeathStateRuntime.enterDeadState(fixture.entry(), 1_000L, 500L);
        when(fixture.agent().getMaxHp()).thenReturn(321);

        AtomicInteger respawnMapId = new AtomicInteger(-1);
        Counters counters = new Counters();
        boolean recovered = AgentRespawnCoordinator.recover(
                fixture.entry(),
                fixture.agent(),
                1_500L,
                new AgentRespawnCoordinator.RecoveryConfig(false, true),
                hooks(fixture, counters, ignored -> AgentRespawnCoordinator.EventReviveResult.NO_EVENT,
                        (agent, mapId) -> {
                            respawnMapId.set(mapId);
                            fixture.currentMap().set(town);
                            fixture.alive().set(true);
                        }, null));

        assertTrue(recovered);
        assertEquals(200, respawnMapId.get());
        assertFalse(AgentDeathStateRuntime.isDead(fixture.entry()));
        counters.assertCompletedOnce();
        verify(fixture.agent()).updateHp(321);
        verify(fixture.agent()).changeFaceExpression(AgentEmote.GLARE.getValue());
    }

    @Test
    void followModeUsesPlayerSpawnNearestLiveFollowTarget() {
        MapleMap deathMap = map(100, false);
        MapleMap followMap = map(300, false);
        AgentFixture fixture = fixture(deathMap);
        Character target = mock(Character.class);
        Point targetPosition = new Point(700, 80);
        Point validSpawn = new Point(640, 100);
        when(target.isLoggedinWorld()).thenReturn(true);
        when(target.isAlive()).thenReturn(true);
        when(target.getMap()).thenReturn(followMap);
        when(target.getPosition()).thenReturn(targetPosition);
        AgentRelationshipRuntime.setFollowTarget(fixture.entry(), target);
        AgentModeStateRuntime.setFollowing(fixture.entry(), true);
        AgentDeathStateRuntime.enterDeadState(fixture.entry(), 1_000L, 500L);

        AtomicReference<Point> spawnReference = new AtomicReference<>();
        AtomicReference<Point> teleportedTo = new AtomicReference<>();
        Counters counters = new Counters();
        boolean recovered = AgentRespawnCoordinator.recover(
                fixture.entry(),
                fixture.agent(),
                1_500L,
                new AgentRespawnCoordinator.RecoveryConfig(true, true),
                hooks(fixture, counters, ignored -> AgentRespawnCoordinator.EventReviveResult.NO_EVENT,
                        (agent, mapId) -> {
                            assertEquals(300, mapId);
                            fixture.currentMap().set(followMap);
                            fixture.alive().set(true);
                        }, (map, reference) -> {
                            assertSame(followMap, map);
                            spawnReference.set(reference);
                            return validSpawn;
                        }, teleportedTo));

        assertTrue(recovered);
        assertSame(targetPosition, spawnReference.get());
        assertSame(validSpawn, teleportedTo.get());
        counters.assertCompletedOnce();
        verify(fixture.agent(), never()).updateHp(anyInt());
    }

    @Test
    void eventHandledReviveDoesNotApplyNormalMapRouting() {
        MapleMap eventMap = map(400, false);
        AgentFixture fixture = fixture(eventMap);
        AgentDeathStateRuntime.enterDeadState(fixture.entry(), 1_000L, 500L);
        AtomicInteger standardRespawns = new AtomicInteger();
        Counters counters = new Counters();

        boolean recovered = AgentRespawnCoordinator.recover(
                fixture.entry(),
                fixture.agent(),
                1_500L,
                new AgentRespawnCoordinator.RecoveryConfig(true, false),
                hooks(fixture, counters, ignored -> {
                    fixture.alive().set(true);
                    return AgentRespawnCoordinator.EventReviveResult.HANDLED_BY_EVENT;
                }, (agent, mapId) -> standardRespawns.incrementAndGet(), null));

        assertTrue(recovered);
        assertEquals(0, standardRespawns.get());
        counters.assertCompletedOnce();
        assertFalse(AgentDeathStateRuntime.isDead(fixture.entry()));
    }

    @Test
    void eventThatHasNotFinishedRevivingRemainsDeadAndRetriesLater() {
        MapleMap eventMap = map(400, false);
        AgentFixture fixture = fixture(eventMap);
        AgentDeathStateRuntime.enterDeadState(fixture.entry(), 1_000L, 500L);
        Counters counters = new Counters();

        boolean recovered = AgentRespawnCoordinator.recover(
                fixture.entry(),
                fixture.agent(),
                1_500L,
                new AgentRespawnCoordinator.RecoveryConfig(true, true),
                hooks(fixture, counters,
                        ignored -> AgentRespawnCoordinator.EventReviveResult.HANDLED_BY_EVENT,
                        (agent, mapId) -> {}, null));

        assertFalse(recovered);
        assertTrue(AgentDeathStateRuntime.isDead(fixture.entry()));
        assertEquals(2_500L, AgentDeathStateRuntime.deadUntilMs(fixture.entry()));
        assertEquals(0, counters.resets.get());
    }

    private static AgentRespawnCoordinator.RecoveryHooks hooks(
            AgentFixture fixture,
            Counters counters,
            AgentRespawnCoordinator.EventReviver eventReviver,
            java.util.function.BiConsumer<Character, Integer> standardRespawn,
            AgentRespawnCoordinator.SpawnPointResolver spawnPointResolver) {
        return hooks(fixture, counters, eventReviver, standardRespawn, spawnPointResolver, new AtomicReference<>());
    }

    private static AgentRespawnCoordinator.RecoveryHooks hooks(
            AgentFixture fixture,
            Counters counters,
            AgentRespawnCoordinator.EventReviver eventReviver,
            java.util.function.BiConsumer<Character, Integer> standardRespawn,
            AgentRespawnCoordinator.SpawnPointResolver spawnPointResolver,
            AtomicReference<Point> teleportedTo) {
        return new AgentRespawnCoordinator.RecoveryHooks(
                eventReviver,
                standardRespawn,
                spawnPointResolver == null ? (map, point) -> null : spawnPointResolver,
                (entry, agent, point) -> teleportedTo.set(point),
                (entry, agent) -> counters.resets.incrementAndGet(),
                (entry, agent) -> counters.broadcasts.incrementAndGet(),
                (agent, message) -> {
                    assertSame(fixture.agent(), agent);
                    assertEquals("back!", message);
                    counters.messages.incrementAndGet();
                });
    }

    private static AgentFixture fixture(MapleMap initialMap) {
        Character agent = mock(Character.class);
        AtomicReference<MapleMap> currentMap = new AtomicReference<>(initialMap);
        AtomicBoolean alive = new AtomicBoolean(false);
        when(agent.getMap()).thenAnswer(ignored -> currentMap.get());
        when(agent.getMapId()).thenAnswer(ignored -> currentMap.get().getId());
        when(agent.isAlive()).thenAnswer(ignored -> alive.get());
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        return new AgentFixture(entry, agent, currentMap, alive);
    }

    private static MapleMap map(int id, boolean town) {
        MapleMap map = mock(MapleMap.class);
        when(map.getId()).thenReturn(id);
        when(map.isTown()).thenReturn(town);
        return map;
    }

    private record AgentFixture(AgentRuntimeEntry entry,
                                Character agent,
                                AtomicReference<MapleMap> currentMap,
                                AtomicBoolean alive) {
    }

    private static final class Counters {
        private final AtomicInteger resets = new AtomicInteger();
        private final AtomicInteger broadcasts = new AtomicInteger();
        private final AtomicInteger messages = new AtomicInteger();

        private void assertCompletedOnce() {
            assertEquals(1, resets.get());
            assertEquals(1, broadcasts.get());
            assertEquals(1, messages.get());
        }
    }
}
