package server.agents.monitoring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.mailbox.AgentMailboxSubmission;
import server.agents.runtime.mailbox.AgentMailboxSubmissionStatus;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentEventReactionMetricsTest {
    @AfterEach
    void resetMetrics() {
        AgentEventReactionMetrics.reset();
    }

    @Test
    void countsAcceptedCoalescedAndRejectedReactionSubmissions() {
        AgentEventReactionMetrics.record("maintenance", submission(AgentMailboxSubmissionStatus.ACCEPTED));
        AgentEventReactionMetrics.record("maintenance", submission(AgentMailboxSubmissionStatus.COALESCED));
        AgentEventReactionMetrics.record("maintenance", submission(AgentMailboxSubmissionStatus.REJECTED_FULL));

        AgentEventReactionMetrics.Snapshot snapshot =
                AgentEventReactionMetrics.snapshots().get("maintenance");
        assertEquals(1, snapshot.accepted());
        assertEquals(1, snapshot.coalesced());
        assertEquals(1, snapshot.rejected());
    }

    private static AgentMailboxSubmission<Void> submission(AgentMailboxSubmissionStatus status) {
        return new AgentMailboxSubmission<>(status, new CompletableFuture<>());
    }
}
