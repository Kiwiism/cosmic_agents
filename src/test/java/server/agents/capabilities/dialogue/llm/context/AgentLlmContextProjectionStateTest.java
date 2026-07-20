package server.agents.capabilities.dialogue.llm.context;

import org.junit.jupiter.api.Test;
import server.agents.operations.events.AgentMapTransitionedEvent;
import server.agents.operations.events.AgentNavigationRouteFailedEvent;
import server.agents.operations.events.AgentRecoveryPerformedEvent;
import server.agents.progression.events.AgentLevelChangedEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentLlmContextProjectionStateTest {
    @Test
    void exposesStructuredCurrentFactsAndClearsRecoveredBlocker() {
        AgentLlmContextProjectionState state = new AgentLlmContextProjectionState();
        state.record(new AgentLevelChangedEvent(7, 10L, 14, 15, 100,
                102000000, "career:7"));
        state.record(new AgentNavigationRouteFailedEvent(7, 20L, 102000000,
                1, 2, 200, 100, "no path", "career:7"));
        state.record(new AgentRecoveryPerformedEvent(7, 30L, 102000000,
                "replan", 10, 20, 15, 20, "career:7"));

        AgentLlmContextProjectionState.Snapshot snapshot = state.snapshot();
        assertEquals("15", snapshot.facts().get("progression.level"));
        assertEquals("100", snapshot.facts().get("progression.jobId"));
        assertEquals("102000000", snapshot.facts().get("world.mapId"));
        assertEquals("career:7", snapshot.facts().get("objective.active"));
        assertEquals("", snapshot.facts().get("navigation.blocker"));
        assertEquals(3, snapshot.milestones().size());
        assertEquals(3, snapshot.revision());
    }

    @Test
    void retainsOnlyTheMostRecentMilestones() {
        AgentLlmContextProjectionState state = new AgentLlmContextProjectionState();
        for (int index = 0; index < AgentLlmContextProjectionState.MAX_MILESTONES + 5; index++) {
            state.record(new AgentMapTransitionedEvent(7, index, 100000000 + index,
                    100000001 + index, -1, "test", "route"));
        }

        AgentLlmContextProjectionState.Snapshot snapshot = state.snapshot();
        assertEquals(AgentLlmContextProjectionState.MAX_MILESTONES, snapshot.milestones().size());
        assertEquals(5L, snapshot.milestones().getFirst().occurredAtMs());
    }
}
