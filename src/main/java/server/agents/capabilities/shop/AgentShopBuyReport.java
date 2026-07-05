package server.agents.capabilities.shop;

public record AgentShopBuyReport(int itemId,
                                 int quantity,
                                 int requestedQuantity,
                                 AgentShopShortfallReason reason) {
    public boolean hasShortfall() {
        return reason != AgentShopShortfallReason.NONE && quantity < requestedQuantity;
    }
}
