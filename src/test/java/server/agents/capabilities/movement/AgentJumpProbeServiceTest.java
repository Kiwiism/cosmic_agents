package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;
import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.Point;

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
    void canReachRopeFromGroundPreservesNullMapLegacyResult() {
        Rope rope = new Rope(100, 40, 120, false);

        assertTrue(AgentJumpProbeService.canReachRopeFromGround(null, new Point(100, 120), rope));
    }
}
