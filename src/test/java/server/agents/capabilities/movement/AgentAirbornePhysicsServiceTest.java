package server.agents.capabilities.movement;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.bots.BotEntry;

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
        BotEntry entry = new BotEntry(agent, null, null);
        AgentBotMovementStateRuntime.setInAir(entry, true);
        AgentBotMovementPhysicsStateRuntime.setPhysicsPosition(entry, new Point(10, 20));

        AgentAirborneStepResult result = AgentAirbornePhysicsService.stepAirborne(entry, agent);

        assertEquals(AgentAirborneStepResult.CONTINUE, result);
        assertTrue(AgentBotMovementStateRuntime.inAir(entry));
    }
}
