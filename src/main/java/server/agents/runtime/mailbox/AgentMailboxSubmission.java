package server.agents.runtime.mailbox;

import java.util.concurrent.CompletableFuture;

public record AgentMailboxSubmission<R>(AgentMailboxSubmissionStatus status,
                                        CompletableFuture<R> result) {
    public AgentMailboxSubmission {
        if (status == null || result == null) {
            throw new IllegalArgumentException("Agent mailbox status and result are required");
        }
    }

    public boolean accepted() {
        return status.accepted();
    }
}
