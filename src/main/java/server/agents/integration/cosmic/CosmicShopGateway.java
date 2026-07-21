package server.agents.integration.cosmic;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.inventory.ItemConstants;
import server.Shop;
import server.ShopFactory;
import server.agents.integration.ShopGateway;
import server.agents.events.AgentEventPriority;
import server.agents.resources.events.AgentResourceEventPublisher;
import server.agents.resources.events.AgentShopTransactionEvent;

public final class CosmicShopGateway implements ShopGateway {
    public static final CosmicShopGateway INSTANCE = new CosmicShopGateway();

    private CosmicShopGateway() {
    }

    @Override
    public Shop findForNpc(int npcId) {
        return ShopFactory.getInstance().getShopForNPC(npcId);
    }

    @Override
    public void sell(Character agent, Shop shop, InventoryType type, short slot, short quantity) {
        Inventory inventory = agent.getInventory(type);
        Item item = inventory == null ? null : inventory.getItem(slot);
        int itemId = item == null ? 0 : item.getItemId();
        int beforeQuantity = count(agent, type, itemId);
        int beforeMeso = agent.getMeso();
        shop.sell(agent.getClient(), type, slot, quantity);
        int currentQuantity = count(agent, type, itemId);
        if (itemId > 0 && currentQuantity < beforeQuantity) {
            publish(agent, shop, "SELL", itemId, beforeQuantity - currentQuantity,
                    agent.getMeso() - beforeMeso, "SUCCESS");
        }
    }

    @Override
    public Shop.TransactionResult recharge(Character agent, Shop shop, short slot) {
        Inventory inventory = agent.getInventory(InventoryType.USE);
        Item item = inventory == null ? null : inventory.getItem(slot);
        int itemId = item == null ? 0 : item.getItemId();
        int beforeQuantity = item == null ? 0 : item.getQuantity();
        int beforeMeso = agent.getMeso();
        Shop.TransactionResult result = shop.rechargeDirect(agent, slot);
        int currentQuantity = item == null ? 0 : item.getQuantity();
        if (itemId > 0) {
            publish(agent, shop, "RECHARGE", itemId,
                    Math.max(0, currentQuantity - beforeQuantity),
                    agent.getMeso() - beforeMeso, result.name());
        }
        return result;
    }

    @Override
    public Shop.TransactionResult buy(Character agent, Shop shop, short slot, int itemId, short quantity) {
        InventoryType type = ItemConstants.getInventoryType(itemId);
        int beforeQuantity = count(agent, type, itemId);
        int beforeMeso = agent.getMeso();
        Shop.TransactionResult result = shop.buyDirect(agent, slot, itemId, quantity);
        int currentQuantity = count(agent, type, itemId);
        publish(agent, shop, "BUY", itemId, Math.max(0, currentQuantity - beforeQuantity),
                agent.getMeso() - beforeMeso, result.name());
        return result;
    }

    private static int count(Character agent, InventoryType type, int itemId) {
        Inventory inventory = agent == null || type == null ? null : agent.getInventory(type);
        return inventory == null || itemId <= 0 ? 0 : inventory.countById(itemId);
    }

    private static void publish(Character agent,
                                Shop shop,
                                String operation,
                                int itemId,
                                int quantity,
                                int mesoDelta,
                                String result) {
        AgentResourceEventPublisher.publishFor(agent,
                objectiveId -> new AgentShopTransactionEvent(
                        agent.getId(), System.currentTimeMillis(), shop.getNpcId(), operation,
                        itemId, quantity, mesoDelta, result, objectiveId),
                "SUCCESS".equals(result) ? AgentEventPriority.NORMAL : AgentEventPriority.IMPORTANT);
    }
}
