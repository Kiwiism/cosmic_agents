package server.agents.runtime.activity.outcome;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.agents.runtime.activity.control.facade.AgentLiveActivityFacade;
import server.agents.runtime.activity.session.AgentActivityExitResult;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.session.AgentActivityPhase;
import server.agents.runtime.activity.session.AgentActivityRollbackPort;
import server.agents.runtime.activity.session.AgentActivitySessionSnapshot;
import server.agents.runtime.activity.session.AgentActivityTerminalOutcome;
import server.agents.runtime.decision.AgentDecisionRecommendation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentActivityOutcomeProcessorTest {
    @TempDir Path directory;

    @Test
    void publishesIdempotentlyAndAcknowledgesOnlyAfterRecommendationIsRecorded() {
        AgentFileActivityOutcomeInbox inbox = new AgentFileActivityOutcomeInbox(directory);
        List<AgentDecisionRecommendation> recorded = new ArrayList<>();
        AgentActivityOutcomeProcessor processor = new AgentActivityOutcomeProcessor(
                inbox, new AgentActivityOutcomeRecoveryPolicy(), recorded::add);
        AgentLiveActivityFacade activity = facade();

        processor.publish(List.of(activity), 2_000L);
        processor.publish(List.of(activity), 2_001L);
        assertEquals(1, inbox.pending("27").size());

        AgentDecisionRecommendation recommendation = processor.recommendNext("27", 2_002L);

        assertNotNull(recommendation);
        assertEquals(List.of(recommendation), recorded);
        assertTrue(inbox.pending("27").isEmpty());
    }

    private AgentLiveActivityFacade facade() {
        AgentActivityTerminalOutcome outcome = new AgentActivityTerminalOutcome(
                AgentActivityKind.HUNTING, AgentActivityPhase.FAILED, "field-1", "27",
                "spawn exhausted", true, 1_000L, 2_000L, Map.of("kills", 10));
        return new AgentLiveActivityFacade(AgentActivityKind.HUNTING,
                new server.agents.runtime.activity.session.AgentActivitySourcePort() {
                    @Override public AgentActivitySessionSnapshot snapshot(long nowMs) {
                        return AgentActivitySessionSnapshot.idle(AgentActivityKind.HUNTING, "27");
                    }
                    @Override public AgentActivityExitResult requestGracefulExit(
                            String reason, long nowMs, long deadlineMs) {
                        return AgentActivityExitResult.released(reason);
                    }
                }, nowMs -> outcome,
                (sessionId, nowMs) -> AgentActivityRollbackPort.Result.rejected("not retained"),
                false, "test facade");
    }
}
