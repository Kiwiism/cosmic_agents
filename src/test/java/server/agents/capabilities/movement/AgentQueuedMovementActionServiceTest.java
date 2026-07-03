package server.agents.capabilities.movement;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.bots.BotEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentQueuedMovementActionServiceTest {
    @Test
    void queueDownJumpPreservesLegacyCrouchAndPendingState() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(1);
        BotEntry entry = new BotEntry(agent, null, null);

        AgentQueuedMovementActionService.queueDownJump(entry, agent);

        assertTrue(AgentBotMovementStateRuntime.downJumpPending(entry));
        assertTrue(AgentBotMovementStateRuntime.crouching(entry));
    }
}
