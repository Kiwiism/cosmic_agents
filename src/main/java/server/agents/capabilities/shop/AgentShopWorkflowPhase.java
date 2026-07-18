package server.agents.capabilities.shop;

public enum AgentShopWorkflowPhase {
    IDLE,
    PLANNED,
    NAVIGATING,
    APPROACHING,
    OPENING,
    TRANSACTING,
    VERIFYING,
    COMPLETED,
    BLOCKED,
    CANCELLED;

    public boolean terminal() {
        return this == COMPLETED || this == BLOCKED || this == CANCELLED;
    }
}
