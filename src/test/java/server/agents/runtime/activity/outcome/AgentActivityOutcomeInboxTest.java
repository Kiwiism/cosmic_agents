package server.agents.runtime.activity.outcome;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.session.AgentActivityPhase;
import server.agents.runtime.activity.session.AgentActivityTerminalOutcome;
import server.agents.runtime.decision.AgentRecommendedAction;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentActivityOutcomeInboxTest {
    @TempDir Path directory;

    @Test
    void outcomeSurvivesRestartAndAcknowledgesExactlyOnce() {
        AgentActivityTerminalOutcome outcome = outcome(AgentActivityPhase.FAILED, true);
        AgentFileActivityOutcomeInbox inbox = new AgentFileActivityOutcomeInbox(directory);
        inbox.publish("field-1:failed", outcome, 2_000L);

        AgentFileActivityOutcomeInbox restarted = new AgentFileActivityOutcomeInbox(directory);
        assertEquals(1, restarted.pending("27").size());
        var acknowledged = restarted.acknowledge("field-1:failed", "advised", 2_100L);
        assertEquals(acknowledged,
                restarted.acknowledge("field-1:failed", "duplicate", 2_200L));
        assertEquals(0, restarted.pending("27").size());
    }

    @Test
    void deterministicRecoveryDistinguishesCompletionRetryAndTerminalFailure() {
        AgentActivityOutcomeRecoveryPolicy policy = new AgentActivityOutcomeRecoveryPolicy();
        assertEquals(AgentRecommendedAction.CONTINUE,
                policy.recommend("complete", outcome(AgentActivityPhase.COMPLETED, false), 3_000L)
                        .action());
        assertEquals(AgentRecommendedAction.RETRY_LOCAL,
                policy.recommend("retry", outcome(AgentActivityPhase.FAILED, true), 3_000L)
                        .action());
        assertEquals(AgentRecommendedAction.SAFE_FALLBACK,
                policy.recommend("failed", outcome(AgentActivityPhase.FAILED, false), 3_000L)
                        .action());
    }

    private static AgentActivityTerminalOutcome outcome(
            AgentActivityPhase phase, boolean retryable) {
        return new AgentActivityTerminalOutcome(AgentActivityKind.HUNTING, phase,
                "field-1", "27", "test outcome", retryable, 1_000L, 1_500L, Map.of());
    }
}
