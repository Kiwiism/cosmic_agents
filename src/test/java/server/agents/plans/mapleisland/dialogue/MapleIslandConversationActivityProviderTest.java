package server.agents.plans.mapleisland.dialogue;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.dialogue.conversation.AgentConversationActivity;
import server.agents.capabilities.dialogue.conversation.AgentConversationActivityRegistry;
import server.agents.plans.amherst.AmherstObjectiveProgress;
import server.agents.plans.amherst.AmherstObjectiveProgressStatus;
import server.agents.plans.amherst.AmherstPlanExecutionState;
import server.agents.plans.amherst.AmherstPlanProgressSnapshot;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MapleIslandConversationActivityProviderTest {
    @Test
    void serviceAdapterExposesPlanStateAsNeutralFacts() {
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        AmherstPlanExecutionState state = mock(AmherstPlanExecutionState.class);
        AmherstObjectiveProgress current = new AmherstObjectiveProgress(
                "r010-0000-kill-mobs", AmherstObjectiveProgressStatus.RUNNING,
                1, "", "", 5_000L, 5_000L, 0L, 0, 0);
        AmherstObjectiveProgress completed = new AmherstObjectiveProgress(
                "r009-0001-complete", AmherstObjectiveProgressStatus.SATISFIED,
                1, "", "", 1_000L, 9_000L, 9_000L, 0, 0);
        when(entry.amherstPlanExecutionState()).thenReturn(state);
        when(state.active()).thenReturn(true);
        when(state.assignedObjectiveId()).thenReturn(current.objectiveId());
        when(state.progress()).thenReturn(new AmherstPlanProgressSnapshot(
                "maple-island", 7, 1L, 9_000L,
                Map.of(current.objectiveId(), current, completed.objectiveId(), completed),
                List.of()));

        AgentConversationActivity activity = AgentConversationActivityRegistry.snapshot(entry, 10_000L);

        assertTrue(activity.objectiveActive());
        assertTrue(activity.hunting());
        assertEquals(5_000L, activity.objectiveAgeMs());
        assertTrue(activity.recentlyCompleted());
    }
}
