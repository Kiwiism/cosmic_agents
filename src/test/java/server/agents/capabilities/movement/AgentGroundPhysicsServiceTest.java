package server.agents.capabilities.movement;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.bots.BotEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentGroundPhysicsServiceTest {
    @Test
    void stopGroundMotionPreservesLegacyVelocityReset() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        BotEntry entry = new BotEntry(agent, null, null);
        AgentBotMovementPhysicsStateRuntime.setHorizontalSpeed(entry, 2.5);
        AgentBotMovementStateRuntime.setMovementVelocity(entry, 7, 3);

        AgentGroundPhysicsService.stopGroundMotion(entry);

        assertEquals(0.0, AgentBotMovementPhysicsStateRuntime.horizontalSpeed(entry));
        assertEquals(7, AgentBotMovementStateRuntime.movementVelocityX(entry));
        assertEquals(3, AgentBotMovementStateRuntime.movementVelocityY(entry));
    }

    @Test
    void velocityFromDeltaXUsesLegacyPacketScaling() {
        assertEquals(160, AgentGroundPhysicsService.velocityFromDeltaX(8));
        assertEquals(-160, AgentGroundPhysicsService.velocityFromDeltaX(-8));
        assertEquals(0, AgentGroundPhysicsService.velocityFromDeltaX(0));
    }
}
