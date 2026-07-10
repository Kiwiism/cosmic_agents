package server.agents.runtime;

import server.agents.capabilities.movement.AgentMapTransitionService;
import client.Character;
import org.junit.jupiter.api.Test;
import server.maps.Foothold;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMapTransitionServiceTest {
    @Test
    void skipsWhenEntryAlreadyTracksCurrentMap() {
        MapleMap map = map(100000000);
        Character agent = character(200, map, map.getId(), new Point(10, 20));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        AgentMapStateRuntime.setMapTracking(entry, map.getId(), Map.of());
        Counters counters = new Counters();

        boolean grounded = AgentMapTransitionService.groundAfterMapChange(entry, agent, hooks(counters, null));

        assertFalse(grounded);
        counters.assertNoSideEffects();
    }

    @Test
    void tracksNewMapAndUsesGroundPointWhenAvailable() {
        MapleMap map = map(100000001);
        Character agent = character(200, map, map.getId(), new Point(10, 20));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        Counters counters = new Counters();
        Point ground = new Point(11, 19);

        boolean grounded = AgentMapTransitionService.groundAfterMapChange(entry, agent, hooks(counters, ground));

        assertTrue(grounded);
        assertTrue(AgentMapStateRuntime.isTrackingMap(entry, map.getId()));
        assertEquals(new Point(10, 19), counters.groundQuery.get());
        assertEquals(ground, counters.teleportPoint.get());
        assertEquals(1, counters.resets.get());
        assertEquals(1, counters.graphWarms.get());
        assertEquals(1, counters.broadcasts.get());
        assertSame(map, counters.graphWarmMap.get());
    }

    @Test
    void fallsBackToCurrentPositionWhenGroundPointIsMissing() {
        MapleMap map = map(100000002);
        Point current = new Point(30, 40);
        Character agent = character(200, map, map.getId(), current);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        Counters counters = new Counters();

        boolean grounded = AgentMapTransitionService.groundAfterMapChange(entry, agent, hooks(counters, null));

        assertTrue(grounded);
        assertEquals(current, counters.teleportPoint.get());
    }

    @Test
    void trackedMapChangeRunsGrindBeforeFollowAndCommonSideEffects() {
        MapleMap map = map(100000003);
        Character agent = character(200, map, map.getId(), new Point(10, 20));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        Counters counters = new Counters();

        boolean handled = AgentMapTransitionService.handleTrackedMapChange(
                entry, agent, mapChangeHooks(counters, true, true));

        assertTrue(handled);
        assertEquals(1, counters.grindIssues.get());
        assertEquals(0, counters.followIssues.get());
        assertEquals(0, counters.partyQuestResets.get());
        assertEquals(1, counters.shopMapChanges.get());
        assertEquals(1, counters.statusChecks.get());
    }

    @Test
    void trackedMapChangeRunsFollowWhenGrindIsNotRequired() {
        MapleMap map = map(100000004);
        Character agent = character(200, map, map.getId(), new Point(10, 20));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        Counters counters = new Counters();

        boolean handled = AgentMapTransitionService.handleTrackedMapChange(
                entry, agent, mapChangeHooks(counters, false, true));

        assertTrue(handled);
        assertEquals(0, counters.grindIssues.get());
        assertEquals(1, counters.followIssues.get());
        assertEquals(0, counters.partyQuestResets.get());
    }

    @Test
    void trackedMapChangeResetsPartyQuestWhenNoModeIsRequired() {
        MapleMap map = map(100000005);
        Character agent = character(200, map, map.getId(), new Point(10, 20));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        Counters counters = new Counters();

        boolean handled = AgentMapTransitionService.handleTrackedMapChange(
                entry, agent, mapChangeHooks(counters, false, false));

        assertTrue(handled);
        assertEquals(0, counters.grindIssues.get());
        assertEquals(0, counters.followIssues.get());
        assertEquals(1, counters.partyQuestResets.get());
    }

    @Test
    void trackedMapChangeSkipsSideEffectsWhenMapIsAlreadyTracked() {
        MapleMap map = map(100000006);
        Character agent = character(200, map, map.getId(), new Point(10, 20));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        AgentMapStateRuntime.setMapTracking(entry, map.getId(), Map.of());
        Counters counters = new Counters();

        boolean handled = AgentMapTransitionService.handleTrackedMapChange(
                entry, agent, mapChangeHooks(counters, true, true));

        assertFalse(handled);
        counters.assertNoSideEffects();
        assertEquals(0, counters.grindIssues.get());
        assertEquals(0, counters.followIssues.get());
        assertEquals(0, counters.partyQuestResets.get());
        assertEquals(0, counters.shopMapChanges.get());
        assertEquals(0, counters.statusChecks.get());
    }

    private static AgentMapTransitionService.GroundingHooks hooks(Counters counters, Point groundPoint) {
        return new AgentMapTransitionService.GroundingHooks(
                map -> {
                    counters.footholdBuilds.incrementAndGet();
                    return Map.<Integer, Foothold>of();
                },
                (map, point) -> {
                    counters.groundQueries.incrementAndGet();
                    counters.groundQuery.set(new Point(point));
                    return groundPoint;
                },
                (entry, agent, point) -> {
                    counters.teleports.incrementAndGet();
                    counters.teleportPoint.set(new Point(point));
                },
                entry -> counters.resets.incrementAndGet(),
                (map, profile) -> {
                    counters.graphWarms.incrementAndGet();
                    counters.graphWarmMap.set(map);
                },
                entry -> counters.broadcasts.incrementAndGet());
    }

    private static AgentMapTransitionService.MapChangeHooks mapChangeHooks(Counters counters,
                                                                           boolean requiresGrind,
                                                                           boolean requiresFollow) {
        return new AgentMapTransitionService.MapChangeHooks(
                hooks(counters, null),
                (entry, agent) -> requiresGrind,
                entry -> counters.grindIssues.incrementAndGet(),
                (entry, agent) -> requiresFollow,
                entry -> counters.followIssues.incrementAndGet(),
                entry -> counters.partyQuestResets.incrementAndGet(),
                (entry, agent) -> counters.shopMapChanges.incrementAndGet(),
                (entry, agent) -> counters.statusChecks.incrementAndGet());
    }

    private static Character character(int id, MapleMap map, int mapId, Point position) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getMapId()).thenReturn(mapId);
        when(character.getMap()).thenReturn(map);
        when(character.getPosition()).thenReturn(new Point(position));
        return character;
    }

    private static MapleMap map(int id) {
        MapleMap map = mock(MapleMap.class);
        when(map.getId()).thenReturn(id);
        return map;
    }

    private static final class Counters {
        private final AtomicInteger footholdBuilds = new AtomicInteger();
        private final AtomicInteger groundQueries = new AtomicInteger();
        private final AtomicInteger teleports = new AtomicInteger();
        private final AtomicInteger resets = new AtomicInteger();
        private final AtomicInteger graphWarms = new AtomicInteger();
        private final AtomicInteger broadcasts = new AtomicInteger();
        private final AtomicInteger grindIssues = new AtomicInteger();
        private final AtomicInteger followIssues = new AtomicInteger();
        private final AtomicInteger partyQuestResets = new AtomicInteger();
        private final AtomicInteger shopMapChanges = new AtomicInteger();
        private final AtomicInteger statusChecks = new AtomicInteger();
        private final AtomicReference<Point> groundQuery = new AtomicReference<>();
        private final AtomicReference<Point> teleportPoint = new AtomicReference<>();
        private final AtomicReference<MapleMap> graphWarmMap = new AtomicReference<>();

        private void assertNoSideEffects() {
            assertEquals(0, footholdBuilds.get());
            assertEquals(0, groundQueries.get());
            assertEquals(0, teleports.get());
            assertEquals(0, resets.get());
            assertEquals(0, graphWarms.get());
            assertEquals(0, broadcasts.get());
        }
    }
}
