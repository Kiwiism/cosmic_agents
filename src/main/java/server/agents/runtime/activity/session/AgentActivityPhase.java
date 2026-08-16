package server.agents.runtime.activity.session;

/** Common lifecycle projection; child systems retain their richer internal phases. */
public enum AgentActivityPhase {
    IDLE,
    ACTIVE,
    SUSPENDING,
    SUSPENDED,
    DRAINING,
    COMPLETED,
    FAILED,
    CANCELLED;

    /** A retained session may be resumed or drained, even while it does not execute. */
    public boolean retainsSession() {
        return this == ACTIVE || this == SUSPENDING || this == SUSPENDED || this == DRAINING;
    }

    /** Only these phases may receive foreground execution ticks. */
    public boolean ownsExecution() {
        return this == ACTIVE || this == SUSPENDING || this == DRAINING;
    }

    /** Compatibility alias for session adapters written before ownership was split. */
    public boolean ownsAgent() {
        return retainsSession();
    }

    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
