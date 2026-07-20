package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;
import server.maps.Foothold;
import server.maps.FootholdTree;
import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentJumpProbeServiceTest {
    @Test
    void simulateJumpLandingPreservesNoFootholdResult() {
        MapleMap map = new MapleMap(910000034, 0, 0, 910000034, 1.0f);

        assertNull(AgentJumpProbeService.simulateJumpLanding(map, new Point(0, 100), 0));
    }

    @Test
    void simulateJumpLandingReturnsAgentOwnedResultType() {
        MapleMap map = new MapleMap(910000034, 0, 0, 910000034, 1.0f);

        AgentJumpLanding landing = AgentJumpProbeService.simulateJumpLanding(map, new Point(0, 100), 0);

        assertNull(landing);
    }

    @Test
    void simulatePostLandingJumpReturnsAgentOwnedResultType() {
        MapleMap map = new MapleMap(910000034, 0, 0, 910000034, 1.0f);

        AgentPostLandingJump landing = AgentJumpProbeService.simulateJumpLandingWithPostLandingTicks(
                map, new Point(0, 100), 0, AgentMovementProfile.base(), 3);

        assertNull(landing);
    }

    @Test
    void simulateFallLandingReturnsAgentOwnedResultType() {
        MapleMap map = new MapleMap(910000034, 0, 0, 910000034, 1.0f);

        AgentJumpLanding landing = AgentJumpProbeService.simulateFallLanding(map, new Point(0, 100), 0);

        assertNull(landing);
    }

    @Test
    void simulateFallLandingReachesFloorBeyondLegacyTimeCap() {
        MapleMap map = new MapleMap(910000035, 0, 0, 910000035, 1.0f);
        map.setMapLineBoundings(-100, 3_000, -500, 500);
        Foothold floor = new Foothold(new Point(-100, 2_500), new Point(100, 2_500), 1);
        FootholdTree footholds = new FootholdTree(new Point(-500, -100), new Point(500, 3_000));
        footholds.insert(floor);
        map.setFootholds(footholds);

        AgentJumpLanding landing = AgentJumpProbeService.simulateFallLanding(map, new Point(0, 0), 0);

        assertNotNull(landing);
        assertEquals(new Point(0, 2_500), landing.point());
        assertTrue(landing.timeMs() > 1_500,
                "fixture must prove the descent exceeds the removed 1500ms simulation cap");
    }

    @Test
    void simulateDownJumpLandingReturnsAgentOwnedResultType() {
        MapleMap map = new MapleMap(910000034, 0, 0, 910000034, 1.0f);

        AgentJumpLanding landing = AgentJumpProbeService.simulateDownJumpLanding(map, new Point(0, 100));

        assertNull(landing);
    }

    @Test
    void simulateWalkOffLandingReturnsAgentOwnedResultType() {
        MapleMap map = new MapleMap(910000034, 0, 0, 910000034, 1.0f);

        AgentWalkOffLanding landing = AgentJumpProbeService.simulateWalkOffLanding(
                map, new Point(0, 100), 1, new AgentGroundTravelState(0.0, 0.0, 0.0),
                AgentMovementProfile.base());

        assertNull(landing);
    }

    @Test
    void canReachRopeFromGroundPreservesNullMapLegacyResult() {
        Rope rope = new Rope(100, 40, 120, false);

        assertTrue(AgentJumpProbeService.canReachRopeFromGround(null, new Point(100, 120), rope));
    }
}
