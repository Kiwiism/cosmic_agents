package server.agents.economy.catalog;

import java.util.List;

public record NpcShopFact(int shopId, int npcId, Integer sourceMapId, List<NpcShopItemFact> items) {
    public NpcShopFact {
        if (shopId <= 0 || npcId <= 0) throw new IllegalArgumentException("shop and NPC ids must be positive");
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record NpcShopItemFact(int itemId, int price, int rechargeableUnitPrice, int buyable) {
        public NpcShopItemFact {
            if (itemId <= 0 || price < 0 || rechargeableUnitPrice < 0 || buyable < 0) {
                throw new IllegalArgumentException("Invalid NPC shop item fact");
            }
        }
    }
}
