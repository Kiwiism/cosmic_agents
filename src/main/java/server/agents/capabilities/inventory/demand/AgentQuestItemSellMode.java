package server.agents.capabilities.inventory.demand;

public enum AgentQuestItemSellMode {
    DISABLED,
    SHADOW,
    ENFORCED;

    public static AgentQuestItemSellMode configured() {
        int value = config.AgentTuning.intValue(
                "server.agents.capabilities.inventory.demand.AgentQuestItemSellMode.MODE");
        if (value < 0 || value >= values().length) {
            throw new IllegalStateException(
                    "AgentQuestItemSellMode.MODE must be 0 (disabled), 1 (shadow), or 2 (enforced)");
        }
        return values()[value];
    }
}
