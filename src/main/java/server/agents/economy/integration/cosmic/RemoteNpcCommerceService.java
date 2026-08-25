package server.agents.economy.integration.cosmic;

import client.Character;
import client.inventory.InventoryType;
import server.Shop;
import server.ShopItem;
import server.agents.economy.catalog.EconomyCatalog;
import server.agents.economy.catalog.NpcShopFact;
import server.agents.integration.ShopGateway;

import java.util.Objects;

/** Temporary FM-only remote door into real Cosmic NPC shop transactions. */
public final class RemoteNpcCommerceService {
    private final EconomyCatalog catalog;
    private final ShopGateway shops;
    private final int entranceMapId;

    public RemoteNpcCommerceService(EconomyCatalog catalog, ShopGateway shops) {
        this(catalog, shops, 910000000);
    }

    public RemoteNpcCommerceService(EconomyCatalog catalog, ShopGateway shops, int entranceMapId) {
        this.catalog = Objects.requireNonNull(catalog);
        this.shops = Objects.requireNonNull(shops);
        if (entranceMapId <= 0) throw new IllegalArgumentException("FM entrance map is required");
        this.entranceMapId = entranceMapId;
    }

    public Receipt buy(Character agent, int npcId, int itemId, short quantity) {
        Access access = access(agent, npcId);
        short slot = findSlot(access.shop(), itemId);
        if (slot < 0) return Receipt.failed(npcId, access.sourceMapId(), "item is not sold by NPC");
        int beforeMeso = agent.getMeso();
        Shop.TransactionResult result = shops.buy(agent, access.shop(), slot, itemId, quantity);
        return receipt(npcId, access.sourceMapId(), result, agent.getMeso() - beforeMeso);
    }

    public Receipt sell(Character agent, int npcId, InventoryType type, short slot, short quantity) {
        Access access = access(agent, npcId);
        int beforeMeso = agent.getMeso();
        Shop.TransactionResult result = shops.sell(agent, access.shop(), type, slot, quantity);
        return receipt(npcId, access.sourceMapId(), result, agent.getMeso() - beforeMeso);
    }

    public Receipt recharge(Character agent, int npcId, short inventorySlot) {
        Access access = access(agent, npcId);
        int beforeMeso = agent.getMeso();
        Shop.TransactionResult result = shops.recharge(agent, access.shop(), inventorySlot);
        return receipt(npcId, access.sourceMapId(), result, agent.getMeso() - beforeMeso);
    }

    private Access access(Character agent, int npcId) {
        if (agent == null || agent.getClient() == null || agent.getMapId() != entranceMapId)
            throw new IllegalStateException(
                    "Remote NPC commerce is available only at the Free Market entrance");
        NpcShopFact fact = catalog.npcShop(npcId)
                .orElseThrow(() -> new IllegalArgumentException("NPC has no real shop: " + npcId));
        if (fact.sourceMapId() == null)
            throw new IllegalStateException("NPC source-map evidence is missing: " + npcId);
        Shop shop = shops.findForNpc(npcId);
        if (shop == null || shop.getId() != fact.shopId())
            throw new IllegalStateException("Live NPC shop does not match pinned catalog");
        return new Access(shop, fact.sourceMapId());
    }

    private static short findSlot(Shop shop, int itemId) {
        for (short slot = 0; slot < shop.getItems().size(); slot++) {
            ShopItem item = shop.getItems().get(slot);
            if (item.getItemId() == itemId) return slot;
        }
        return -1;
    }

    private static Receipt receipt(int npcId, int mapId, Shop.TransactionResult result, int mesoDelta) {
        return new Receipt(result == Shop.TransactionResult.SUCCESS, result.name(), npcId, mapId,
                mesoDelta, "REMOTE_FROM_FREE_MARKET_ENTRANCE");
    }

    private record Access(Shop shop, int sourceMapId) { }
    public record Receipt(boolean success, String result, int npcId, int sourceMapId,
                          int mesoDelta, String accessMode) {
        private static Receipt failed(int npcId, int mapId, String message) {
            return new Receipt(false, message, npcId, mapId, 0, "REMOTE_FROM_FREE_MARKET_ENTRANCE");
        }
    }
}
