package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentGroundCollisionServiceTest {
    @Test
    void nullMapGroundQueriesPreserveLegacySafeDefaults() {
        Point point = new Point(10, 20);

        assertFalse(AgentGroundCollisionService.canWalkGroundStep(null, point, 8));
        assertFalse(AgentGroundCollisionService.isGroundStepBlockedByWall(null, point, 8));
        assertFalse(AgentGroundCollisionService.canStartDownJump(null, point));
        assertTrue(AgentGroundCollisionService.isGroundFarBelow(null, point));
    }
}
