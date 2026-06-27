package server.bots;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotMovementCommandRuntime;

import java.awt.Point;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class AgentBotMovementCommandRuntimeTest {
    @Test
    void movementCommandFacadeDelegatesToLegacyBotManagerCommands() {
        BotEntry entry = new BotEntry(null, null, null);
        Point dest = new Point(10, 20);
        Point patrolPos = new Point(30, 40);
        BotManager manager = mock(BotManager.class);

        try (MockedStatic<BotManager> botManager = mockStatic(BotManager.class)) {
            botManager.when(BotManager::getInstance).thenReturn(manager);

            AgentBotMovementCommandRuntime.followOwner(entry);
            AgentBotMovementCommandRuntime.stop(entry);
            AgentBotMovementCommandRuntime.moveTo(entry, dest, true);
            AgentBotMovementCommandRuntime.farmHere(entry, dest);
            AgentBotMovementCommandRuntime.patrol(entry, patrolPos);
            AgentBotMovementCommandRuntime.grind(entry);

            verify(manager).issueFollowOwner(entry);
            verify(manager).issueStop(entry);
            verify(manager).issueMoveTo(entry, dest, true);
            verify(manager).issueFarmHere(entry, dest);
            verify(manager).issuePatrol(entry, patrolPos);
            verify(manager).issueGrind(entry);
        }
    }
}
