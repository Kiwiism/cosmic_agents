package server.agents.capabilities.shop;

import server.agents.runtime.AgentRuntimeHandle;
import server.Shop;

@FunctionalInterface
public interface AgentShopPurchaseAction<E extends AgentRuntimeHandle> {
    AgentShopPurchaseSequence<E> run(AgentShopPurchaseSequence<E> sequence, Shop shop);
}
