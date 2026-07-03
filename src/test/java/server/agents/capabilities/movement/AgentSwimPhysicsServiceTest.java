package server.agents.capabilities.movement;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotSwimStateRuntime;
import server.bots.BotEntry;

import java.awt.Point;

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
        BotEntry entry = new BotEntry(agent, null, null);

        AgentSwimPhysicsService.applySwimMotion(entry);

        assertTrue(AgentBotSwimStateRuntime.swimming(entry));
    }
}
