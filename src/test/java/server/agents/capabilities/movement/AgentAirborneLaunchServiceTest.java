package server.agents.capabilities.movement;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
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

        assertTrue(AgentBotMovementStateRuntime.inAir(entry));
        assertFalse(AgentBotMovementStateRuntime.crouching(entry));
        assertFalse(AgentBotMovementStateRuntime.downJumpPending(entry));
        assertEquals(launchPosition, AgentBotMovementPhysicsStateRuntime.roundedPhysicsPosition(entry));
        assertEquals(-4.5f, AgentBotMovementPhysicsStateRuntime.verticalVelocity(entry));
        assertEquals(3, AgentBotMovementPhysicsStateRuntime.airVelocityX(entry));
        assertEquals(0.0, AgentBotMovementPhysicsStateRuntime.airSteerVelocityX(entry));
        assertFalse(AgentBotMovementPhysicsStateRuntime.fixedAirArc(entry));
        assertTrue(AgentBotClimbStateRuntime.climbUpIntent(entry));
    }
}
