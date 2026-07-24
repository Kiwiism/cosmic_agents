package server.agents.coordination.session;

public enum AgentInteractionSessionStatus {
    PROPOSED,
    ACTIVE,
    COMPLETED,
    DECLINED,
    CANCELLED,
    EXPIRED;

    public boolean terminal() {
        return this == COMPLETED || this == DECLINED
                || this == CANCELLED || this == EXPIRED;
    }
}
