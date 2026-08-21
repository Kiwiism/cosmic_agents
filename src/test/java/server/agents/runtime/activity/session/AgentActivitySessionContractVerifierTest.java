package server.agents.runtime.activity.session;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentActivitySessionContractVerifierTest {
    @Test
    void acceptsValidIdleOwningAndTerminalProjections() {
        assertTrue(AgentActivitySessionContractVerifier.snapshotIssues(
                AgentActivitySessionSnapshot.idle(AgentActivityKind.QUESTING, "27")).isEmpty());
        assertTrue(AgentActivitySessionContractVerifier.snapshotIssues(
                new AgentActivitySessionSnapshot(AgentActivityKind.QUESTING,
                        AgentActivityPhase.ACTIVE, "session", "request", "caller", "27",
                        1_000L, "")).isEmpty());
        assertTrue(AgentActivitySessionContractVerifier.terminalIssues(
                new AgentActivityTerminalOutcome(AgentActivityKind.QUESTING,
                        AgentActivityPhase.COMPLETED, "session", "27", "done", false,
                        1_000L, 2_000L, Map.of())).isEmpty());
    }
}
