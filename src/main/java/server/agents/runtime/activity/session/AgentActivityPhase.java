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

    public boolean ownsAgent() {
        return this == ACTIVE || this == SUSPENDING || this == SUSPENDED || this == DRAINING;
    }

    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
