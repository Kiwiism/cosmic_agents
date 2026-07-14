package server.agents.runtime.scheduler;

public final class AgentQuiescenceException extends RuntimeException {
    public enum Reason {
        CLOSED,
        STALE_SESSION,
        TIMEOUT,
        INVALID_TOKEN
    }

    private final Reason reason;

    public AgentQuiescenceException(Reason reason, String message) {
        super(message);
        if (reason == null) {
            throw new IllegalArgumentException("Agent quiescence failure reason is required");
        }
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
