package server.agents.capabilities.trade;

import client.inventory.Item;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService.AgentEquipTradeGroups;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy.AmmoTradeGroups;

import java.util.List;
import java.util.function.Supplier;

public final class AgentTradeItemCollectionCallbackService {
    private AgentTradeItemCollectionCallbackService() {
    }

    public static AgentTradeItemCollectionService.TradeItemCollectionCallbacks tradeItemCollectionCallbacks(
            Supplier<List<Item>> recommendedItems,
            Supplier<AgentEquipTradeGroups> equipTradeGroups,
            Supplier<AmmoTradeGroups> ammoTradeGroups) {
        return AgentTradeItemCollectionService.TradeItemCollectionCallbacks.of(
                recommendedItems,
                equipTradeGroups,
                ammoTradeGroups);
    }
}
