package server.agents.capabilities.movement;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentAirbornePhysicsServiceTest {
    @Test
    void stepAirborneMapsLegacyContinueResult() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(1);
        when(agent.getMap()).thenReturn(null);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentMovementStateRuntime.setInAir(entry, true);
        AgentMovementPhysicsStateRuntime.setPhysicsPosition(entry, new Point(10, 20));

        AgentAirborneStepResult result = AgentAirbornePhysicsService.stepAirborne(entry, agent);

        assertEquals(AgentAirborneStepResult.CONTINUE, result);
        assertTrue(AgentMovementStateRuntime.inAir(entry));
    }

    @Test
    void stepAirborneAppliesAgentOwnedAirSteering() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(1);
        when(agent.getMap()).thenReturn(null);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentMovementStateRuntime.setInAir(entry, true);
        AgentMovementStateRuntime.setMoveDirection(entry, 1);
        AgentMovementPhysicsStateRuntime.setPhysicsPosition(entry, new Point(10, 20));

        AgentAirborneStepResult result = AgentAirbornePhysicsService.stepAirborne(entry, agent);

        assertEquals(AgentAirborneStepResult.CONTINUE, result);
        assertEquals(0.5, AgentMovementPhysicsStateRuntime.airSteerVelocityX(entry));
        assertEquals(1, AgentMovementStateRuntime.facingDirection(entry));
    }
}
