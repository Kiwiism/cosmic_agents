package server.agents.capabilities.inventory;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import server.ItemRestrictionPolicy;
import server.agents.integration.InventoryGateway;

import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

public final class AgentInventoryCollectionService {
    private AgentInventoryCollectionService() {
    }

    public static List<Item> collectFromBag(Character agent,
                                            InventoryType type,
                                            Predicate<Item> filter,
                                            InventoryGateway inventory) {
        return collectFromBag(
                agent,
                type,
                filter,
                inventory::isQuestItem,
                itemId -> ItemRestrictionPolicy.allowsUntradeable(agent, itemId));
    }

    public static List<Item> collectFromBag(Character agent,
                                            InventoryType type,
                                            Predicate<Item> filter,
                                            IntPredicate isQuestItem,
                                            IntPredicate allowsUntradeableItem) {
        return AgentInventoryItemPolicy.collectSafeItems(agent, type, filter, isQuestItem, allowsUntradeableItem);
    }

    public static List<Item> collectFromBag(Character agent,
                                            InventoryType type,
                                            Predicate<Item> filter,
                                            IntPredicate isQuestItem,
                                            boolean untradeableItemsTradeable) {
        return AgentInventoryItemPolicy.collectSafeItems(agent, type, filter, isQuestItem, untradeableItemsTradeable);
    }
}
