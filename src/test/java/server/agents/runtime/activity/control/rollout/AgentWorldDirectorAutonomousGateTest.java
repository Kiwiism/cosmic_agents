package server.agents.runtime.activity.control.rollout;

import org.junit.jupiter.api.Test;
import server.agents.runtime.activity.AgentActivityOwnershipReconciliation;
import server.agents.runtime.activity.control.facade.AgentLiveActivityFacade;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.world.AgentWorldActivityRequestType;
import server.agents.runtime.activity.world.AgentWorldCompletionPolicy;
import server.agents.runtime.activity.world.AgentWorldDirective;
import server.agents.runtime.activity.world.AgentWorldDirectiveSource;
import server.agents.runtime.activity.world.AgentWorldDirectiveType;
import server.agents.runtime.activity.world.AgentWorldDirectorMode;
import server.agents.runtime.activity.world.AgentWorldInterruptionPolicy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWorldDirectorAutonomousGateTest {
    @Test
    void defaultsFailClosedAndEnabledGateRequiresEvidenceAndFailureBudget() {
        assertFalse(new AgentWorldDirectorAutonomousGate(
                AgentWorldDirectorAutonomousConfig.disabled()).inspect(
                AgentWorldDirectorMode.AUTONOMOUS, directive(), facade(), clean(),
                100L, 0, 0).permitted());

        AgentWorldDirectorAutonomousGate gate = new AgentWorldDirectorAutonomousGate(
                new AgentWorldDirectorAutonomousConfig(true, 0, Set.of(27), 1,
                        20L, 0, Set.of(AgentActivityKind.HUNTING), true));
        assertFalse(gate.inspect(AgentWorldDirectorMode.AUTONOMOUS, directive(), facade(),
                clean(), 19L, 0, 0).permitted());
        assertFalse(gate.inspect(AgentWorldDirectorMode.AUTONOMOUS, directive(), facade(),
                clean(), 20L, 1, 0).permitted());
        assertTrue(gate.inspect(AgentWorldDirectorMode.AUTONOMOUS, directive(), facade(),
                clean(), 20L, 0, 0).permitted());
    }

    private static AgentWorldDirective directive() {
        return new AgentWorldDirective(1, "auto", 27,
                AgentWorldDirectiveType.TRANSFER_ACTIVITY, AgentWorldDirectiveSource.POLICY,
                null, AgentActivityKind.HUNTING, AgentWorldActivityRequestType.FIELD_VISIT,
                "field:auto", Map.of(), AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                AgentWorldCompletionPolicy.RETURN_TO_PREVIOUS_ACTIVITY, 1, 1_000L, 0L, "test");
    }

    private static AgentLiveActivityFacade facade() {
        return new AgentLiveActivityFacade(AgentActivityKind.QUESTING,
                new server.agents.runtime.activity.session.AgentActivitySourcePort() {
                    @Override public server.agents.runtime.activity.session.AgentActivitySessionSnapshot
                            snapshot(long nowMs) { return null; }
                    @Override public server.agents.runtime.activity.session.AgentActivityExitResult
                            requestGracefulExit(String reason, long nowMs, long deadlineMs) {
                        return server.agents.runtime.activity.session.AgentActivityExitResult.released(reason);
                    }
                }, nowMs -> null,
                (sessionId, nowMs) ->
                        server.agents.runtime.activity.session.AgentActivityRollbackPort.Result.resumed("ok"),
                true, "rollback ready");
    }

    private static AgentActivityOwnershipReconciliation clean() {
        return new AgentActivityOwnershipReconciliation(
                AgentActivityOwnershipReconciliation.Status.CLEAN,
                AgentActivityKind.QUESTING, List.of(AgentActivityKind.QUESTING), "clean");
    }
}
