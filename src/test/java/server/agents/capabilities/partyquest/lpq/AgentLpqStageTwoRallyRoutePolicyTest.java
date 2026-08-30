package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.navigation.AgentNavigationMapLoader;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLpqStageTwoRallyRoutePolicyTest {
    @Test
    void leftOverlayCannotReturnToTheLowerJunction() {
        assertEquals(List.of(21, 20, 19, 18, 47, 11, 45, 10, 9, 8, 7, 6, 5, 4, 3),
                AgentLpqStageTwoRallyRoutePolicy.route(
                        AgentLpqStageTwoRallyRoutePolicy.LEFT_BRANCH));
        assertEquals(21, AgentLpqStageTwoRallyRoutePolicy.nextRegionId(1, 48));
        assertEquals(20, AgentLpqStageTwoRallyRoutePolicy.nextRegionId(1, 21));
        assertEquals(19, AgentLpqStageTwoRallyRoutePolicy.nextRegionId(1, 20));
        assertEquals(18, AgentLpqStageTwoRallyRoutePolicy.nextRegionId(1, 19));
        assertEquals(47, AgentLpqStageTwoRallyRoutePolicy.nextRegionId(1, 18));
        assertEquals(11, AgentLpqStageTwoRallyRoutePolicy.nextRegionId(1, 47));
        assertEquals(45, AgentLpqStageTwoRallyRoutePolicy.nextRegionId(1, 11));
        assertEquals(10, AgentLpqStageTwoRallyRoutePolicy.nextRegionId(1, 45));
        assertEquals(5, AgentLpqStageTwoRallyRoutePolicy.nextRegionId(1, 6));
        assertEquals(4, AgentLpqStageTwoRallyRoutePolicy.nextRegionId(1, 5));
        assertEquals(3, AgentLpqStageTwoRallyRoutePolicy.nextRegionId(1, 4));
        assertEquals(-1, AgentLpqStageTwoRallyRoutePolicy.nextRegionId(1, 3));
    }

    @Test
    void rightOverlayCannotReturnToTheLowerJunction() {
        assertEquals(List.of(23, 22, 46, 17, 16, 15, 14, 13, 12,
                        11, 45, 10, 9, 8, 7, 6, 5, 4, 3),
                AgentLpqStageTwoRallyRoutePolicy.route(
                        AgentLpqStageTwoRallyRoutePolicy.RIGHT_BRANCH));
        assertEquals(21, AgentLpqStageTwoRallyRoutePolicy.nextRegionId(2, 48));
        assertEquals(20, AgentLpqStageTwoRallyRoutePolicy.nextRegionId(2, 21));
        assertEquals(22, AgentLpqStageTwoRallyRoutePolicy.nextRegionId(2, 23));
        assertEquals(46, AgentLpqStageTwoRallyRoutePolicy.nextRegionId(2, 22));
        assertEquals(17, AgentLpqStageTwoRallyRoutePolicy.nextRegionId(2, 46));
        assertEquals(16, AgentLpqStageTwoRallyRoutePolicy.nextRegionId(2, 17));
        assertEquals(15, AgentLpqStageTwoRallyRoutePolicy.nextRegionId(2, 16));
        assertEquals(14, AgentLpqStageTwoRallyRoutePolicy.nextRegionId(2, 15));
        assertEquals(13, AgentLpqStageTwoRallyRoutePolicy.nextRegionId(2, 14));
        assertEquals(12, AgentLpqStageTwoRallyRoutePolicy.nextRegionId(2, 13));
        assertEquals(11, AgentLpqStageTwoRallyRoutePolicy.nextRegionId(2, 12));
        assertEquals(45, AgentLpqStageTwoRallyRoutePolicy.nextRegionId(2, 11));
        assertEquals(-1, AgentLpqStageTwoRallyRoutePolicy.nextRegionId(2, 3));
    }

    @Test
    void rallyOverlaysUseRealAuthoredMovementEdgesAllTheWayToTheBalloon() {
        MapleMap map = AgentNavigationMapLoader.loadMapGeometry(922_010_200);
        AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(map);

        for (int branch : List.of(
                AgentLpqStageTwoRallyRoutePolicy.LEFT_BRANCH,
                AgentLpqStageTwoRallyRoutePolicy.RIGHT_BRANCH)) {
            List<Integer> route = AgentLpqStageTwoRallyRoutePolicy.route(branch);
            for (int index = 0; index + 1 < route.size(); index++) {
                int from = route.get(index);
                int to = route.get(index + 1);
                assertTrue(graph.hasInterRegionEdge(from, to), () ->
                        "Stage 2 branch " + branch + " lacks authored edge "
                                + from + " -> " + to + "; outgoing="
                                + graph.getOutgoing(from).stream()
                                .map(edge -> edge.toRegionId + ":" + edge.type)
                                .toList());
            }
        }
    }

    @Test
    void ropeFortyFiveAdvancesUpwardInsteadOfFallingBackToPlatformEleven() {
        MapleMap map = AgentNavigationMapLoader.loadMapGeometry(922_010_200);
        AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(map);
        AgentLpqMemberState member = new AgentLpqMemberState(
                72_045, AgentLpqMemberState.MemberType.AGENT);

        Point waypoint = AgentLpqStageTwoRallyRoutePolicy.nextWaypoint(
                map, new Point(24, -2_202), member);

        assertEquals(graph.getRegion(10).centerPoint(), waypoint);
    }
}
