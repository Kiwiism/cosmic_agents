package server.agents.capabilities.navigation;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;
import server.maps.FootholdTree;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentFootholdDetourServiceTest {
    @Test
    void holdsAwayFirstBranchDetourUntilCrossed() {
        Foothold current = foothold(1, 0, 100, 100, 100);
        Foothold bridge = foothold(2, 100, 100, 200, 100);
        Foothold launch = foothold(3, 200, 100, 0, -100);
        current.setNext(2);
        bridge.setPrev(1);
        bridge.setNext(3);
        launch.setPrev(2);
        MapleMap map = map(current, bridge, launch);
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentNavigationGraph.Region region = new AgentNavigationGraph.Region(1, List.of(
                new AgentNavigationGraph.Segment(current),
                new AgentNavigationGraph.Segment(bridge),
                new AgentNavigationGraph.Segment(launch)));
        AgentNavigationGraph graph = new AgentNavigationGraph(
                map.getId(), 1, List.of(region), Map.of(1, region), Map.of(), Map.of(), Set.of());
        AgentNavigationGraph.Edge edge = new AgentNavigationGraph.Edge(
                1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(10, -91), new Point(300, 200), 10, 10, 1, 0, 0, 0, 0, 100);

        assertEquals(new Point(200, 100),
                AgentFootholdDetourService.waypoint(entry, graph, new Point(50, 100), edge));
        assertTrue(AgentFootholdDetourService.active(entry));
        assertEquals(new Point(200, 100),
                AgentFootholdDetourService.waypoint(entry, graph, new Point(120, 100), edge));

        assertNull(AgentFootholdDetourService.waypoint(entry, graph, new Point(201, 100), edge));
        assertFalse(AgentFootholdDetourService.active(entry));
    }

    @Test
    void clearsDetourWhenEdgeChanges() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentNavigationGraph.Edge first = edge(1);
        AgentNavigationGraph.Edge second = edge(2);
        entry.navigationEdgeState().setFootholdDetour(first, new Point(100, 100));

        assertNull(AgentFootholdDetourService.waypoint(entry, null, new Point(0, 100), second));
        assertFalse(AgentFootholdDetourService.active(entry));
    }

    private static AgentNavigationGraph.Edge edge(int destination) {
        return new AgentNavigationGraph.Edge(1, destination, AgentNavigationGraph.EdgeType.JUMP,
                new Point(-80, 49), new Point(200, 200), -80, -80, 1, 0, 0, 0, 0, 100);
    }

    private static Foothold foothold(int id, int x1, int y1, int x2, int y2) {
        return new Foothold(new Point(x1, y1), new Point(x2, y2), id);
    }

    private static MapleMap map(Foothold... footholds) {
        MapleMap map = new MapleMap(910000071, 0, 0, 910000071, 1.0f);
        FootholdTree tree = new FootholdTree(new Point(-1000, -1000), new Point(1000, 1000));
        for (Foothold foothold : footholds) {
            tree.insert(foothold);
        }
        map.setFootholds(tree);
        return map;
    }
}
