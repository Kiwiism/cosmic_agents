package server.agents.integration;

import client.Character;
import client.inventory.InventoryType;
import server.Shop;

@AgentGatewayAffinity(
        value = AgentGatewayThreadAffinity.SHARD_SAFE_DIRECT,
        rationale = "Shop transactions validate and mutate only the owning Agent inventory and meso state.")
public interface ShopGateway {
    Shop findForNpc(int npcId);

    void sell(Character agent, Shop shop, InventoryType type, short slot, short quantity);

    Shop.TransactionResult recharge(Character agent, Shop shop, short slot);

    Shop.TransactionResult buy(Character agent, Shop shop, short slot, int itemId, short quantity);
}
