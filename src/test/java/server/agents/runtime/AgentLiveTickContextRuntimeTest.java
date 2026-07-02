package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.bots.BotEntry;
import server.bots.BotMovementManager;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentLiveTickContextRuntimeTest {
    @Test
    void preparesLiveTickContextThroughAgentRuntimeHooks() {
        Character agent = mock(Character.class);
        Character leader = mock(Character.class);
        Character followAnchor = mock(Character.class);
        Point agentPosition = new Point(10, 20);
        Point rawLeaderPosition = new Point(30, 40);
        Point targetPosition = new Point(50, 60);
        when(agent.getPosition()).thenReturn(agentPosition);
        BotEntry entry = new BotEntry(agent, leader, null);
        AgentTargetSnapshot snapshot = new AgentTargetSnapshot(
                new AgentFormationService.FormationState(AgentFormationService.FormationType.STAGGER, 0, 0),
                rawLeaderPosition,
                rawLeaderPosition,
                "leader",
                rawLeaderPosition,
                targetPosition,
                null,
                null,
                null,
                targetPosition,
                "follow");

        try (MockedStatic<BotMovementManager> movementManager = mockStatic(BotMovementManager.class)) {
            AgentLiveTickContextService.Context context = AgentLiveTickContextRuntime.prepareLiveTickContext(
                    entry,
                    agent,
                    leader,
                    (tickEntry, tickLeader) -> followAnchor,
                    tickEntry -> snapshot);

            movementManager.verify(() -> BotMovementManager.refreshMovementProfile(entry));
            assertEquals(agentPosition, context.agentPosition());
            assertSame(followAnchor, context.followAnchor());
            assertSame(snapshot, context.targetSnapshot());
            assertEquals(targetPosition, context.targetPosition());
        }
    }
}
