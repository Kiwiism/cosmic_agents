package server.agents.progression.questwork;

/** Durable lifecycle of one independently selected quest. */
public enum AgentQuestWorkPhase {
    SELECTED,
    ACTIVE,
    SUSPEND_REQUESTED,
    SUSPENDED,
    COMPLETED,
    FAILED,
    ABANDONED;

    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == ABANDONED;
    }
}
