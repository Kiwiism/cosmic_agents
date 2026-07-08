package server.agents.capabilities.movement;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMotionTimerServiceTest {
    @Test
    void tickMotionTimersPreservesLegacyDownJumpGraceCountdown() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentMovementStateRuntime.setDownJumpGracePeriodMs(entry, 100);

        AgentMotionTimerService.tickMotionTimers(entry);

        assertTrue(AgentMovementStateRuntime.downJumpGracePeriodMs(entry) < 100);
    }
}
