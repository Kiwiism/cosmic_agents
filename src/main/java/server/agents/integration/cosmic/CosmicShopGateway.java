package server.agents.integration.cosmic;

import client.Character;
import client.inventory.InventoryType;
import server.Shop;
import server.agents.integration.ShopGateway;

public final class CosmicShopGateway implements ShopGateway {
    public static final CosmicShopGateway INSTANCE = new CosmicShopGateway();

    private CosmicShopGateway() {
    }

    @Override
    public void sell(Character agent, Shop shop, InventoryType type, short slot, short quantity) {
        shop.sell(agent.getClient(), type, slot, quantity);
    }

    @Override
    public Shop.TransactionResult recharge(Character agent, Shop shop, short slot) {
        return shop.rechargeDirect(agent, slot);
    }
}
