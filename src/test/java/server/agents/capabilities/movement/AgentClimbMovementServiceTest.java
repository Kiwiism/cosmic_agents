package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.bots.BotEntry;
import server.maps.Rope;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentClimbMovementServiceTest {
    @Test
    void holdClimbIdleUsesAgentMovementDistances() {
        BotEntry entry = new BotEntry(null, null, null);

        assertTrue(AgentClimbMovementService.shouldHoldClimbIdle(entry, 0, 0));
        assertFalse(AgentClimbMovementService.shouldHoldClimbIdle(entry, AgentMovementPhysicsConfig.configuredStopDist(), 0));
    }

    @Test
    void snapToClimbTargetAllowsPreciseBottomAnchor() {
        BotEntry entry = new BotEntry(null, null, null);
        Rope rope = new Rope(10, 100, 300, false);
        AgentBotClimbStateRuntime.setClimbingOnRope(entry, rope);
        AgentBotNavigationDebugStateRuntime.setNavPreciseTarget(entry, true);

        assertTrue(AgentClimbMovementService.shouldSnapToClimbTarget(entry, new Point(10, 300), 1));
    }
}
