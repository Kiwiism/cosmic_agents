package server.agents.capabilities.objective;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.maps.MapleMap;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentNpcInteractionReachabilityServiceTest {
    @Test
    void treatsNpcWithoutGraphRegionAsUnavailable() {
        MapleMap map = mock(MapleMap.class);
        AgentNavigationGraph graph = mock(AgentNavigationGraph.class);
        Point current = new Point(516, 122);
        Point npc = new Point(133, -86);
        when(graph.findRegionId(map, current)).thenReturn(8);
        when(graph.findRegionId(map, npc)).thenReturn(-1);

        assertTrue(AgentNpcInteractionReachabilityService.graphRouteUnavailable(
                graph, map, current, npc));
    }

    @Test
    void doesNotTreatMissingGraphOrKnownSameRegionAsUnavailable() {
        MapleMap map = mock(MapleMap.class);
        AgentNavigationGraph graph = mock(AgentNavigationGraph.class);
        Point current = new Point(0, 100);
        Point npc = new Point(500, 100);
        when(graph.findRegionId(map, current)).thenReturn(4);
        when(graph.findRegionId(map, npc)).thenReturn(4);

        assertFalse(AgentNpcInteractionReachabilityService.graphRouteUnavailable(
                null, map, current, npc));
        assertFalse(AgentNpcInteractionReachabilityService.graphRouteUnavailable(
                graph, map, current, npc));
    }
}
