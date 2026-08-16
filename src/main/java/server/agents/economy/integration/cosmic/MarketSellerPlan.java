package server.agents.economy.integration.cosmic;

import client.inventory.InventoryType;
import server.agents.capabilities.shop.AgentFreeMarketStallService;

import java.util.List;

public record MarketSellerPlan(List<NpcSale> npcSales,
                               List<AgentFreeMarketStallService.Listing> stallListings,
                               int preferredRoomMapId, String stallDescription) {
    public MarketSellerPlan {
        npcSales = List.copyOf(npcSales); stallListings = List.copyOf(stallListings);
    }
    public record NpcSale(int npcId, InventoryType inventoryType, short slot, short quantity,
                          int itemId, String reason, String evidence) { }
}
