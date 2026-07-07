package server.agents.capabilities.movement;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotSwimStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSwimPhysicsServiceTest {
    @Test
    void applySwimMotionPreservesLegacySwimStateTransition() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(1);
        when(agent.getMap()).thenReturn(null);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        AgentSwimPhysicsService.applySwimMotion(entry);

        assertTrue(AgentBotSwimStateRuntime.swimming(entry));
    }

    @Test
    void applySwimMotionAppliesAgentOwnedHorizontalIntent() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(1);
        when(agent.getMap()).thenReturn(null);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentBotSwimStateRuntime.setSwimMoveDirection(entry, 1);

        AgentSwimPhysicsService.applySwimMotion(entry);

        assertEquals(1, AgentBotMovementStateRuntime.facingDirection(entry));
        assertTrue(AgentBotMovementPhysicsStateRuntime.horizontalSpeed(entry) > 0.0);
        assertTrue(AgentBotSwimStateRuntime.swimming(entry));
    }
}
