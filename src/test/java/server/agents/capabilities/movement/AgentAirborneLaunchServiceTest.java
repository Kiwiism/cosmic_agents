package server.agents.capabilities.movement;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentClimbStateRuntime;
import server.agents.integration.AgentMovementPhysicsStateRuntime;
import server.agents.integration.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentAirborneLaunchServiceTest {
    @Test
    void launchAirborneInitializesAirStateFromAgentEntry() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Point launchPosition = new Point(120, 300);

        AgentAirborneLaunchService.launchAirborne(entry, launchPosition, -4.5f, 3, true);

        assertTrue(AgentMovementStateRuntime.inAir(entry));
        assertFalse(AgentMovementStateRuntime.crouching(entry));
        assertFalse(AgentMovementStateRuntime.downJumpPending(entry));
        assertEquals(launchPosition, AgentMovementPhysicsStateRuntime.roundedPhysicsPosition(entry));
        assertEquals(-4.5f, AgentMovementPhysicsStateRuntime.verticalVelocity(entry));
        assertEquals(3, AgentMovementPhysicsStateRuntime.airVelocityX(entry));
        assertEquals(0.0, AgentMovementPhysicsStateRuntime.airSteerVelocityX(entry));
        assertFalse(AgentMovementPhysicsStateRuntime.fixedAirArc(entry));
        assertTrue(AgentClimbStateRuntime.climbUpIntent(entry));
    }
}
