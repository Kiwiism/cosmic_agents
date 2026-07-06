package server.agents.capabilities.movement;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentKnockbackMovementServiceTest {
    @Test
    void beginKnockbackPreservesFacingDirectionAndStartsAirborneMotion() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentBotMovementStateRuntime.setFacingDirection(entry, -1);

        AgentKnockbackMovementService.beginKnockback(entry, agent, new Point(10, 20), -4.0f, 3);

        assertTrue(AgentBotMovementStateRuntime.inAir(entry));
        assertEquals(-1, AgentBotMovementStateRuntime.facingDirection(entry));
        assertEquals(3, AgentBotMovementPhysicsStateRuntime.airVelocityX(entry));
    }

    @Test
    void applyAirKnockbackPreservesFacingDirectionAndUpdatesAirVelocity() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentBotMovementStateRuntime.setFacingDirection(entry, 1);
        AgentBotMovementStateRuntime.setInAir(entry, true);

        AgentKnockbackMovementService.applyAirKnockback(entry, agent, -5);

        assertTrue(AgentBotMovementStateRuntime.inAir(entry));
        assertEquals(1, AgentBotMovementStateRuntime.facingDirection(entry));
        assertEquals(-5, AgentBotMovementPhysicsStateRuntime.airVelocityX(entry));
    }
}
