package server.agents.runtime.activity.world;

public enum AgentWorldDirectiveStatus {
    PENDING,
    CLAIMED,
    COMPLETED,
    REJECTED,
    CANCELLED,
    EXPIRED;

    public boolean terminal() {
        return this == COMPLETED || this == REJECTED || this == CANCELLED || this == EXPIRED;
    }
}
