package server.agents.integration;

public record AgentBotShopBuyReport(int itemId,
                                    int quantity,
                                    int requestedQuantity,
                                    AgentBotShopShortfallReason reason) {
    public boolean hasShortfall() {
        return reason != AgentBotShopShortfallReason.NONE && quantity < requestedQuantity;
    }
}
