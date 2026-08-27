package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentLpqExitRoutePolicyTest {
    @Test
    void fixesEverySplitRoomExitPortalInsteadOfRediscoveringIt() {
        for (int room = 922_010_401; room <= 922_010_405; room++) {
            assertEquals(2, AgentLpqExitRoutePolicy.portalId(room, 922_010_400));
        }
        assertEquals(3, AgentLpqExitRoutePolicy.portalId(922_010_501, 922_010_500));
        for (int room = 922_010_502; room <= 922_010_506; room++) {
            assertEquals(2, AgentLpqExitRoutePolicy.portalId(room, 922_010_500));
        }
    }

    @Test
    void specializedStageFiveRoomsUseTheirFlowAppropriateExit() {
        assertEquals(java.util.List.of(3),
                AgentLpqExitRoutePolicy.portalIds(922_010_501, 922_010_500));
        assertEquals(java.util.List.of(2),
                AgentLpqExitRoutePolicy.portalIds(922_010_506, 922_010_500));
        assertEquals(java.util.List.of(2),
                AgentLpqExitRoutePolicy.portalIds(922_010_505, 922_010_500));
    }

    @Test
    void standardStageFiveRoomsUseTheirAuthoredTwoRopeWaypoints() {
        Point portal = new Point(-147, -3_535);
        for (int room = 922_010_502; room <= 922_010_506; room++) {
            assertEquals(new Point(-8, -2_488), AgentLpqExitRoutePolicy.nextWaypoint(
                    room, 922_010_500, new Point(215, -2_014), portal));
            assertEquals(new Point(13, -3_533), AgentLpqExitRoutePolicy.nextWaypoint(
                    room, 922_010_500, new Point(-8, -2_488), portal));
        }
        assertNull(AgentLpqExitRoutePolicy.nextWaypoint(
                922_010_501, 922_010_500, new Point(215, -2_014), portal));
    }

    @Test
    void stageTwoTrapUsesItsAuthoredPortalAndTwoRopeExit() {
        Point portal = new Point(-157, -3_888);
        assertEquals(2, AgentLpqExitRoutePolicy.portalId(922_010_201, 922_010_200));
        assertEquals(new Point(-8, -2_489), AgentLpqExitRoutePolicy.nextWaypoint(
                922_010_201, 922_010_200, new Point(0, -462), portal));
        assertEquals(new Point(13, -3_885), AgentLpqExitRoutePolicy.nextWaypoint(
                922_010_201, 922_010_200, new Point(-8, -2_489), portal));
    }

    @Test
    void unknownOrDownwardRoutesRemainOnGenericNavigation() {
        assertNull(AgentLpqExitRoutePolicy.portalId(100_000_000, 100_000_001));
        assertNull(AgentLpqExitRoutePolicy.nextWaypoint(
                922_010_700, 922_010_800, new Point(228, -1_543), new Point(179, -774)));
    }
}
