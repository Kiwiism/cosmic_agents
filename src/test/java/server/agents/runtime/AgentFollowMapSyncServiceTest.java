package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotModeStateRuntime;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentFollowMapSyncServiceTest {
    @Test
    void skipsWhenAgentIsNotFollowing() {
        MapleMap agentMap = map(100000000);
        MapleMap anchorMap = map(200000000);
        Character agent = character(agentMap, 100000000, new Point(10, 10));
        Character anchor = character(anchorMap, 200000000, new Point(20, 30));
        AgentRuntimeEntry entry = entry(agent);
        Counters counters = new Counters(null);

        boolean synced = AgentFollowMapSyncService.syncFollowMap(entry, agent, anchor, hooks(counters));

        assertFalse(synced);
        counters.assertNoSideEffects();
    }

    @Test
    void skipsWhenAnchorIsMissingOrAlreadyOnSameMap() {
        MapleMap map = map(100000000);
        Character agent = character(map, 100000000, new Point(10, 10));
        Character anchor = character(map, 100000000, new Point(20, 30));
        AgentRuntimeEntry entry = entry(agent);
        AgentBotModeStateRuntime.setFollowing(entry, true);
        Counters counters = new Counters(null);

        assertFalse(AgentFollowMapSyncService.syncFollowMap(entry, agent, null, hooks(counters)));
        assertFalse(AgentFollowMapSyncService.syncFollowMap(entry, agent, anchor, hooks(counters)));
        counters.assertNoSideEffects();
    }

    @Test
    void changesMapToGroundedAnchorPosition() {
        MapleMap agentMap = map(100000000);
        MapleMap anchorMap = map(200000000);
        Character agent = character(agentMap, 100000000, new Point(10, 10));
        Character anchor = character(anchorMap, 200000000, new Point(20, 30));
        AgentRuntimeEntry entry = entry(agent);
        AgentBotModeStateRuntime.setFollowing(entry, true);
        Point ground = new Point(21, 29);
        Counters counters = new Counters(ground);

        boolean synced = AgentFollowMapSyncService.syncFollowMap(entry, agent, anchor, hooks(counters));

        assertTrue(synced);
        assertEquals(new Point(20, 29), counters.groundQuery.get());
        assertSame(anchorMap, counters.changedMap.get());
        assertEquals(ground, counters.changedPosition.get());
        counters.assertSyncSideEffects();
    }

    @Test
    void fallsBackToAnchorPositionWhenGroundIsMissing() {
        MapleMap agentMap = map(100000000);
        MapleMap anchorMap = map(200000000);
        Character agent = character(agentMap, 100000000, new Point(10, 10));
        Point anchorPosition = new Point(20, 30);
        Character anchor = character(anchorMap, 200000000, anchorPosition);
        AgentRuntimeEntry entry = entry(agent);
        AgentBotModeStateRuntime.setFollowing(entry, true);
        Counters counters = new Counters(null);

        boolean synced = AgentFollowMapSyncService.syncFollowMap(entry, agent, anchor, hooks(counters));

        assertTrue(synced);
        assertEquals(anchorPosition, counters.changedPosition.get());
        counters.assertSyncSideEffects();
    }

    private static AgentFollowMapSyncService.FollowMapSyncHooks hooks(Counters counters) {
        return new AgentFollowMapSyncService.FollowMapSyncHooks(
                (map, point) -> {
                    counters.groundQueries.incrementAndGet();
                    counters.groundQuery.set(new Point(point));
                    return counters.groundPoint;
                },
                (entry, agent) -> counters.idleGrounds.incrementAndGet(),
                (agent, map, point) -> {
                    counters.mapChanges.incrementAndGet();
                    counters.changedMap.set(map);
                    counters.changedPosition.set(new Point(point));
                },
                entry -> counters.resets.incrementAndGet());
    }

    private static AgentRuntimeEntry entry(Character agent) {
        return new AgentRuntimeEntry(agent, mock(Character.class), null);
    }

    private static Character character(MapleMap map, int mapId, Point position) {
        Character character = mock(Character.class);
        when(character.getMap()).thenReturn(map);
        when(character.getMapId()).thenReturn(mapId);
        when(character.getPosition()).thenReturn(new Point(position));
        return character;
    }

    private static MapleMap map(int id) {
        MapleMap map = mock(MapleMap.class);
        when(map.getId()).thenReturn(id);
        return map;
    }

    private static final class Counters {
        private final Point groundPoint;
        private final AtomicInteger groundQueries = new AtomicInteger();
        private final AtomicInteger idleGrounds = new AtomicInteger();
        private final AtomicInteger mapChanges = new AtomicInteger();
        private final AtomicInteger resets = new AtomicInteger();
        private final AtomicReference<Point> groundQuery = new AtomicReference<>();
        private final AtomicReference<MapleMap> changedMap = new AtomicReference<>();
        private final AtomicReference<Point> changedPosition = new AtomicReference<>();

        private Counters(Point groundPoint) {
            this.groundPoint = groundPoint;
        }

        private void assertSyncSideEffects() {
            assertEquals(1, groundQueries.get());
            assertEquals(1, idleGrounds.get());
            assertEquals(1, mapChanges.get());
            assertEquals(1, resets.get());
        }

        private void assertNoSideEffects() {
            assertEquals(0, groundQueries.get());
            assertEquals(0, idleGrounds.get());
            assertEquals(0, mapChanges.get());
            assertEquals(0, resets.get());
        }
    }
}
