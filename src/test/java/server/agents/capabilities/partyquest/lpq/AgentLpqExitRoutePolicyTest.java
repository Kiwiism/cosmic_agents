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
        for (int room = 922_010_502; room <= 922_010_505; room++) {
            assertEquals(new Point(-8, -2_488), AgentLpqExitRoutePolicy.nextWaypoint(
                    room, 922_010_500, new Point(215, -2_014), portal));
            assertEquals(new Point(13, -2_562), AgentLpqExitRoutePolicy.nextWaypoint(
                    room, 922_010_500, new Point(-8, -2_488), portal));
            assertEquals(new Point(13, -3_533), AgentLpqExitRoutePolicy.nextWaypoint(
                    room, 922_010_500, new Point(13, -2_562), portal));
        }
        assertNull(AgentLpqExitRoutePolicy.nextWaypoint(
                922_010_501, 922_010_500, new Point(215, -2_014), portal));
    }

    @Test
    void darkSightRoomUsesEveryAuthoredRopeTowardTheTopPortal() {
        Point portal = new Point(-147, -3_535);
        assertEquals(new Point(-123, -252), AgentLpqExitRoutePolicy.nextWaypoint(
                922_010_506, 922_010_500, new Point(-186, -190), portal));
        assertEquals(new Point(-123, -428), AgentLpqExitRoutePolicy.nextWaypoint(
                922_010_506, 922_010_500, new Point(-123, -252), portal));
        assertEquals(new Point(118, -452), AgentLpqExitRoutePolicy.nextWaypoint(
                922_010_506, 922_010_500, new Point(-123, -428), portal));
        assertEquals(new Point(-8, -2_488), AgentLpqExitRoutePolicy.nextWaypoint(
                922_010_506, 922_010_500, new Point(-8, -1_518), portal));
        assertEquals(new Point(13, -2_562), AgentLpqExitRoutePolicy.nextWaypoint(
                922_010_506, 922_010_500, new Point(-8, -2_488), portal));
        assertEquals(new Point(13, -3_533), AgentLpqExitRoutePolicy.nextWaypoint(
                922_010_506, 922_010_500, new Point(13, -2_562), portal));
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
    void stageFourNpcRegroupReusesTheAuthoredMainMapAscent() {
        Point balloon = new Point(57, -2_184);
        assertEquals(new Point(-15, -820), AgentLpqExitRoutePolicy.nextWaypoint(
                922_010_400, 922_010_500, new Point(0, -100), balloon));
        assertEquals(new Point(4, -1_561), AgentLpqExitRoutePolicy.nextWaypoint(
                922_010_400, 922_010_500, new Point(-15, -820), balloon));
        assertEquals(new Point(-28, -1_946), AgentLpqExitRoutePolicy.nextWaypoint(
                922_010_400, 922_010_500, new Point(4, -1_561), balloon));
        assertEquals(new Point(1, -2_169), AgentLpqExitRoutePolicy.nextWaypoint(
                922_010_400, 922_010_500, new Point(-28, -1_946), balloon));
    }

    @Test
    void stageOneBottomMemberUsesTheLastAuthoredRopeBackToTheBalloon() {
        Point portal = new Point(-38, -180);
        assertEquals(new Point(-117, 85), AgentLpqExitRoutePolicy.nextWaypoint(
                922_010_100, 922_010_200, new Point(-40, 130), portal));
        assertEquals(new Point(-117, -178), AgentLpqExitRoutePolicy.nextWaypoint(
                922_010_100, 922_010_200, new Point(-117, 85), portal));
    }

    @Test
    void everyStageFourRoomCrossesToAndClimbsTheFarLeftExitRope() {
        for (int room = 922_010_401; room <= 922_010_405; room++) {
            Point portal = new Point(-1_360, -81);
            assertEquals(new Point(180, 165), AgentLpqExitRoutePolicy.nextWaypoint(
                    room, 922_010_400, new Point(206, -107), portal));
            assertEquals(new Point(-180, 165), AgentLpqExitRoutePolicy.nextWaypoint(
                    room, 922_010_400, new Point(180, 165), portal));
            assertEquals(new Point(-450, 225), AgentLpqExitRoutePolicy.nextWaypoint(
                    room, 922_010_400, new Point(-180, 165), portal));
            assertEquals(new Point(-720, 225), AgentLpqExitRoutePolicy.nextWaypoint(
                    room, 922_010_400, new Point(-450, 225), portal));
            assertEquals(new Point(-1_023, 165), AgentLpqExitRoutePolicy.nextWaypoint(
                    room, 922_010_400, new Point(-720, 225), portal));
            assertEquals(new Point(-720, 225), AgentLpqExitRoutePolicy.nextWaypoint(
                    room, 922_010_400, new Point(-712, 225), portal));
            assertEquals(new Point(-1_260, 285), AgentLpqExitRoutePolicy.nextWaypoint(
                    room, 922_010_400, new Point(-1_023, 165), portal));
            assertEquals(new Point(-1_306, 213), AgentLpqExitRoutePolicy.nextWaypoint(
                    room, 922_010_400, new Point(-1_260, 285), portal));
            assertEquals(new Point(-1_306, -73), AgentLpqExitRoutePolicy.nextWaypoint(
                    room, 922_010_400, new Point(-1_306, 213), portal));
            assertNull(AgentLpqExitRoutePolicy.nextWaypoint(
                    room, 922_010_400, new Point(-1_306, -73), portal));
        }
    }

    @Test
    void unknownOrDownwardRoutesRemainOnGenericNavigation() {
        assertNull(AgentLpqExitRoutePolicy.portalId(100_000_000, 100_000_001));
        assertNull(AgentLpqExitRoutePolicy.nextWaypoint(
                922_010_700, 922_010_800, new Point(228, -1_543), new Point(179, -774)));
    }
}
