package server.agents.capabilities.movement;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.bots.BotEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMotionTimerServiceTest {
    @Test
    void tickMotionTimersPreservesLegacyDownJumpGraceCountdown() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        BotEntry entry = new BotEntry(agent, null, null);
        AgentBotMovementStateRuntime.setDownJumpGracePeriodMs(entry, 100);

        AgentMotionTimerService.tickMotionTimers(entry);

        assertTrue(AgentBotMovementStateRuntime.downJumpGracePeriodMs(entry) < 100);
    }
}
