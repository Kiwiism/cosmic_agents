package server.agents.integration;

import client.Character;
import client.inventory.InventoryType;
import server.Shop;

public interface ShopGateway {
    void sell(Character agent, Shop shop, InventoryType type, short slot, short quantity);

    Shop.TransactionResult recharge(Character agent, Shop shop, short slot);

    Shop.TransactionResult buy(Character agent, Shop shop, short slot, int itemId, short quantity);
}
