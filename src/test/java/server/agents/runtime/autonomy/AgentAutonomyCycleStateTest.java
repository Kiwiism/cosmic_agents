package server.agents.runtime.autonomy;

import org.junit.jupiter.api.Test;
import server.agents.model.AgentPosition;
import server.agents.model.AgentSnapshot;
import server.agents.perception.AgentPerceptionSnapshot;
import server.agents.plans.AgentPlanExecutionStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAutonomyCycleStateTest {
    @Test
    void completesOnlyTheMatchingActiveDecisionCycle() {
        AgentAutonomyCycleState state = new AgentAutonomyCycleState();
        AgentAutonomySnapshot snapshot = new AgentAutonomySnapshot(
                state.nextSnapshotSequence(),
                100L,
                new AgentSnapshot(7, "Trace", 10000, 10, 0,
                        new AgentPosition(12, 34), true),
                AgentPerceptionSnapshot.unavailable());
        AgentAutonomyCycleRecord active = state.begin(
                snapshot, "progression", "maple-island", "1",
                "talk", "npc-quest", List.of("npc", "quest"),
                "chain:talk:1");

        assertFalse(active.complete());
        assertNull(state.complete(
                "different", AgentPlanExecutionStatus.SUCCEEDED, "wrong", 101L));

        AgentAutonomyCycleRecord completed = state.complete(
                "chain:talk:1", AgentPlanExecutionStatus.SUCCEEDED, "done", 102L);

        assertTrue(completed.complete());
        assertEquals(AgentPlanExecutionStatus.SUCCEEDED, completed.resultStatus());
        assertEquals("done", completed.resultReason());
        assertEquals(completed, state.latest());
    }
}
