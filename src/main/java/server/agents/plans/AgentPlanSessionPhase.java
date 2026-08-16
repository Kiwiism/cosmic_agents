package server.agents.plans;

/** External ownership lifecycle for the universal plan executor. */
public enum AgentPlanSessionPhase {
    IDLE,
    ACTIVE,
    SUSPENDING,
    SUSPENDED,
    DRAINING,
    COMPLETED,
    BLOCKED,
    FAILED,
    CANCELLED;

    public boolean ownsAgent() {
        return this == ACTIVE || this == SUSPENDING || this == SUSPENDED || this == DRAINING;
    }

    public boolean terminal() {
        return this == COMPLETED || this == BLOCKED || this == FAILED || this == CANCELLED;
    }
}
