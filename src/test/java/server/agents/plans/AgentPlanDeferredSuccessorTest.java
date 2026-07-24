package server.agents.plans;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentPlanDeferredSuccessorTest {
    @Test
    void deferredSuccessorSurvivesPlanStartAndCheckpointRestore() {
        AgentPlanSessionState state = new AgentPlanSessionState();
        AgentPlanDefinition plan =
                AgentPlanRepository.defaultRepository().require("maple-island-full-mvp");

        state.deferSuccessor("southperry-to-lith-harbor");
        state.start(plan, "chain:91:1", AgentPlanStartRequest.EMPTY, 100L);

        assertEquals("southperry-to-lith-harbor", state.deferredSuccessorPlanId());

        AgentPlanCheckpoint checkpoint = state.pendingCheckpoint(91, 200L);
        AgentPlanSessionState restored = new AgentPlanSessionState();
        restored.restore(checkpoint);

        assertEquals("southperry-to-lith-harbor", restored.deferredSuccessorPlanId());
        restored.clearDeferredSuccessor("southperry-to-lith-harbor");
        assertEquals("", restored.deferredSuccessorPlanId());
    }
}
