package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentMapStateRuntime;
import server.maps.Foothold;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMovementOnlyMapChangeServiceTest {
    @Test
    void fallsThroughWhenMapIsAlreadyTracked() {
        Character agent = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        when(agent.getMapId()).thenReturn(1000);
        when(agent.getMap()).thenReturn(map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        AgentMapStateRuntime.setMapTracking(entry, 1000, Collections.emptyMap());
        AtomicInteger teleports = new AtomicInteger();

        boolean handled = AgentMovementOnlyMapChangeService.handleMapChange(
                entry,
                agent,
                hooks(new Point(5, 6), teleports, new AtomicReference<>()));

        assertFalse(handled);
        assertEquals(0, teleports.get());
    }

    @Test
    void tracksNewMapAndTeleportsToGroundPoint() {
        Character agent = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        when(agent.getMapId()).thenReturn(2000);
        when(agent.getMap()).thenReturn(map);
        when(agent.getPosition()).thenReturn(new Point(50, 100));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        AtomicInteger teleports = new AtomicInteger();
        AtomicReference<Point> teleportedTo = new AtomicReference<>();

        boolean handled = AgentMovementOnlyMapChangeService.handleMapChange(
                entry,
                agent,
                hooks(new Point(50, 90), teleports, teleportedTo));

        assertTrue(handled);
        assertTrue(AgentMapStateRuntime.isTrackingMap(entry, 2000));
        assertEquals(1, teleports.get());
        assertEquals(new Point(50, 90), teleportedTo.get());
    }

    @Test
    void fallsBackToCurrentPositionWhenGroundPointIsMissing() {
        Character agent = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        Point current = new Point(50, 100);
        when(agent.getMapId()).thenReturn(3000);
        when(agent.getMap()).thenReturn(map);
        when(agent.getPosition()).thenReturn(current);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        AtomicReference<Point> teleportedTo = new AtomicReference<>();

        boolean handled = AgentMovementOnlyMapChangeService.handleMapChange(
                entry,
                agent,
                hooks(null, new AtomicInteger(), teleportedTo));

        assertTrue(handled);
        assertEquals(current, teleportedTo.get());
    }

    private static AgentMovementOnlyMapChangeService.Hooks hooks(Point ground,
                                                                 AtomicInteger teleports,
                                                                 AtomicReference<Point> teleportedTo) {
        return new AgentMovementOnlyMapChangeService.Hooks(
                map -> Map.<Integer, Foothold>of(),
                (map, point) -> ground,
                (entry, agent, position) -> {
                    teleports.incrementAndGet();
                    teleportedTo.set(position);
                },
                entry -> {
                },
                entry -> {
                },
                (entry, agent) -> {
                },
                (entry, agent) -> {
                });
    }
}
