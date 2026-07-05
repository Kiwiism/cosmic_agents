package server.agents.capabilities.shop;

import server.Shop;

@FunctionalInterface
public interface AgentShopPurchaseAction {
    AgentShopPurchaseSequence run(AgentShopPurchaseSequence sequence, Shop shop);
}
