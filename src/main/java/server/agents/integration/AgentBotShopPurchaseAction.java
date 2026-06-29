package server.agents.integration;

import server.Shop;

@FunctionalInterface
public interface AgentBotShopPurchaseAction {
    AgentBotShopPurchaseSequence run(AgentBotShopPurchaseSequence sequence, Shop shop);
}
