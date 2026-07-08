package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentClimbStateRuntime;
import server.agents.integration.AgentNavigationDebugStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Rope;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentClimbMovementServiceTest {
    @Test
    void holdClimbIdleUsesAgentMovementDistances() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertTrue(AgentClimbMovementService.shouldHoldClimbIdle(entry, 0, 0));
        assertFalse(AgentClimbMovementService.shouldHoldClimbIdle(entry, AgentMovementPhysicsConfig.configuredStopDist(), 0));
    }

    @Test
    void snapToClimbTargetAllowsPreciseBottomAnchor() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Rope rope = new Rope(10, 100, 300, false);
        AgentClimbStateRuntime.setClimbingOnRope(entry, rope);
        AgentNavigationDebugStateRuntime.setNavPreciseTarget(entry, true);

        assertTrue(AgentClimbMovementService.shouldSnapToClimbTarget(entry, new Point(10, 300), 1));
    }
}
