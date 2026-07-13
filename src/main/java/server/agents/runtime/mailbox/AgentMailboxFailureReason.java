package server.agents.runtime.mailbox;

public enum AgentMailboxFailureReason {
    CLOSED,
    FULL,
    STALE_SESSION,
    EXPIRED,
    COALESCED,
    DISCARDED
}
