package server.agents.runtime.mailbox;

public enum AgentMailboxSubmissionStatus {
    ACCEPTED,
    COALESCED,
    REJECTED_CLOSED,
    REJECTED_FULL,
    REJECTED_EXPIRED;

    public boolean accepted() {
        return this == ACCEPTED || this == COALESCED;
    }
}
