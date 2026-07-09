package server.agents.capabilities.inventory;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import config.YamlConfig;
import server.agents.integration.cosmic.CosmicAgentServerAdapter;

import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

public final class AgentInventoryCollectionService {
    private AgentInventoryCollectionService() {
    }

    public static List<Item> collectFromBag(Character agent, InventoryType type, Predicate<Item> filter) {
        return collectFromBag(
                agent,
                type,
                filter,
                CosmicAgentServerAdapter.INSTANCE.inventory()::isQuestItem,
                YamlConfig.config.server.UNTRADEABLE_ITEMS_TRADEABLE);
    }

    public static List<Item> collectFromBag(Character agent,
                                            InventoryType type,
                                            Predicate<Item> filter,
                                            IntPredicate isQuestItem,
                                            boolean untradeableItemsTradeable) {
        return AgentInventoryItemPolicy.collectSafeItems(agent, type, filter, isQuestItem, untradeableItemsTradeable);
    }
}
