package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLiveTickContextServiceTest {
    @Test
    void preparesLiveTickContextInLegacyOrder() {
        Character agent = mock(Character.class);
        Character leader = mock(Character.class);
        Character followAnchor = mock(Character.class);
        Point agentPosition = new Point(10, 20);
        Point rawLeaderPosition = new Point(30, 40);
        Point targetPosition = new Point(50, 60);
        when(agent.getPosition()).thenReturn(agentPosition);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, null);
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
        List<String> calls = new ArrayList<>();

        AgentLiveTickContextService.Context context = AgentLiveTickContextService.prepareLiveTickContext(
                entry,
                agent,
                leader,
                new AgentLiveTickContextService.Hooks(
                        tickEntry -> calls.add("refresh"),
                        (tickEntry, tickLeader) -> {
                            calls.add("followAnchor");
                            return followAnchor;
                        },
                        tickEntry -> {
                            calls.add("snapshot");
                            return snapshot;
                        },
                        (tickEntry, rawPosition) -> {
                            calls.add("observe");
                            assertEquals(rawLeaderPosition, rawPosition);
                        },
                        (tickEntry, rawPosition) -> {
                            calls.add("remember");
                            assertEquals(rawLeaderPosition, rawPosition);
                        },
                        (tickEntry, tickAgent) -> calls.add("farmCleanup"),
                        (tickEntry, tickAgent) -> calls.add("patrolCleanup"),
                        (tickEntry, tickAgentPosition, tickSnapshot) -> {
                            calls.add("moveWindow");
                            assertEquals(agentPosition, tickAgentPosition);
                            assertSame(snapshot, tickSnapshot);
                        }));

        assertEquals(List.of(
                "refresh",
                "followAnchor",
                "snapshot",
                "observe",
                "remember",
                "farmCleanup",
                "patrolCleanup",
                "moveWindow"), calls);
        assertEquals(agentPosition, context.agentPosition());
        assertSame(followAnchor, context.followAnchor());
        assertSame(snapshot, context.targetSnapshot());
        assertEquals(targetPosition, context.targetPosition());
    }
}
