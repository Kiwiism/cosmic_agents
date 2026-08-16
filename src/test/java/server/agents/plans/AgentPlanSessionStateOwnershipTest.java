package server.agents.plans;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPlanSessionStateOwnershipTest {
    @Test
    void ownershipAndPendingSuspendSurviveCheckpointRoundTrip() {
        AgentPlanSessionState state = new AgentPlanSessionState();
        AgentPlanDefinition plan = plan();
        state.start(plan, "chain-1", AgentPlanStartRequest.EMPTY, 1_000L);
        AgentPlanSessionHandle handle = new AgentPlanSessionHandle(
                "session-1", "request-1", "caller-1", 42, plan.planId(), 1_000L);
        state.own(handle);
        state.requestExit(AgentPlanExitRequest.suspend(handle, "temporary detour", 1_500L, 8_000L));

        AgentPlanCheckpoint checkpoint = state.pendingCheckpoint(42, 1_600L);
        assertNotNull(checkpoint);
        assertEquals(2, checkpoint.schemaVersion());

        AgentPlanSessionState restored = new AgentPlanSessionState();
        restored.restore(checkpoint);

        assertEquals(handle, restored.sessionHandle());
        assertEquals(AgentPlanSessionPhase.SUSPENDING, restored.phase());
        assertEquals(AgentPlanExitMode.SUSPEND_AFTER_STEP, restored.pendingExitMode());
        assertEquals(8_000L, restored.pendingExitDeadlineMs());
    }

    @Test
    void terminalStateProducesCoordinatorOutcome() {
        AgentPlanSessionState state = new AgentPlanSessionState();
        AgentPlanDefinition plan = plan();
        state.start(plan, "chain-2", AgentPlanStartRequest.EMPTY, 2_000L);
        state.own(new AgentPlanSessionHandle(
                "session-2", "request-2", "caller-2", 43, plan.planId(), 2_000L));
        state.terminal(AgentPlanExecutionStatus.FAILED, "quest resource unavailable");
        state.captureOutcome(3_000L);

        assertNotNull(state.lastOutcome());
        assertEquals(AgentPlanSessionPhase.FAILED, state.lastOutcome().phase());
        assertTrue(state.lastOutcome().retryable());
    }

    private static AgentPlanDefinition plan() {
        return new AgentPlanDefinition(
                1, "test-plan", "1", "test", "test",
                new AgentPlanDefinition.ObjectivePolicy(
                        "test", 1, 0L, 0,
                        server.agents.objectives.AgentObjectiveSource.OPERATOR_COMMAND,
                        "test", AgentPlanDefinition.Registration.EXECUTOR),
                List.of(), List.of(), List.of(), List.of());
    }
}
