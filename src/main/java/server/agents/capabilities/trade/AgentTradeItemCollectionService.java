package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService.AgentEquipTradeGroups;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy.AmmoTradeGroups;
import server.agents.capabilities.inventory.AgentInventoryTradeCollectionService;

import java.util.List;
import java.util.function.Supplier;

public final class AgentTradeItemCollectionService {
    private AgentTradeItemCollectionService() {
    }

    public static List<Item> collectItems(String category,
                                          Character agent,
                                          Character owner,
                                          TradeItemCollectionCallbacks callbacks) {
        return AgentInventoryTradeCollectionService.collectItems(
                category,
                agent,
                owner,
                callbacks::recommendedItems,
                callbacks::equipTradeGroups,
                callbacks::ammoTradeGroups);
    }

    public interface TradeItemCollectionCallbacks {
        List<Item> recommendedItems();
        AgentEquipTradeGroups equipTradeGroups();
        AmmoTradeGroups ammoTradeGroups();

        static TradeItemCollectionCallbacks of(Supplier<List<Item>> recommendedItems,
                                               Supplier<AgentEquipTradeGroups> equipTradeGroups,
                                               Supplier<AmmoTradeGroups> ammoTradeGroups) {
            return new TradeItemCollectionCallbacks() {
                @Override
                public List<Item> recommendedItems() {
                    return recommendedItems.get();
                }

                @Override
                public AgentEquipTradeGroups equipTradeGroups() {
                    return equipTradeGroups.get();
                }

                @Override
                public AmmoTradeGroups ammoTradeGroups() {
                    return ammoTradeGroups.get();
                }
            };
        }
    }
}
