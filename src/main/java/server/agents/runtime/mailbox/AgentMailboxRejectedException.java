package server.agents.runtime.mailbox;

import java.util.concurrent.RejectedExecutionException;

public final class AgentMailboxRejectedException extends RejectedExecutionException {
    private final AgentMailboxFailureReason reason;

    public AgentMailboxRejectedException(AgentMailboxFailureReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public AgentMailboxFailureReason reason() {
        return reason;
    }
}
