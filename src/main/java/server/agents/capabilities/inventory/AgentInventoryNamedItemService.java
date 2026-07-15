package server.agents.capabilities.inventory;

import client.Character;
import client.inventory.Item;
import server.ItemRestrictionPolicy;
import server.agents.capabilities.dialogue.AgentItemQueryNormalizer;
import server.agents.integration.InventoryGateway;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

public final class AgentInventoryNamedItemService {
    private static final Map<Integer, String> normalizedItemNameCache = new ConcurrentHashMap<>();

    private AgentInventoryNamedItemService() {
    }

    public static int countNamedItems(Character agent, String fragment, InventoryGateway inventory) {
        return AgentInventoryTradePolicy.itemQuantitySum(collectNamedItems(agent, fragment, inventory));
    }

    public static List<Item> collectNamedItems(Character agent, String fragment, InventoryGateway inventory) {
        return collectNamedItems(
                agent,
                fragment,
                itemId -> normalizedItemName(itemId, inventory),
                inventory::isQuestItem,
                itemId -> ItemRestrictionPolicy.allowsUntradeable(agent, itemId));
    }

    static List<Item> collectNamedItems(Character agent,
                                        String fragment,
                                        IntFunction<String> normalizedItemName,
                                        IntPredicate isQuestItem,
                                        IntPredicate allowsUntradeableItem) {
        return AgentInventoryItemPolicy.collectNamedItems(
                agent,
                fragment,
                AgentItemQueryNormalizer::normalize,
                normalizedItemName,
                isQuestItem,
                allowsUntradeableItem);
    }

    static List<Item> collectNamedItems(Character agent,
                                        String fragment,
                                        IntFunction<String> normalizedItemName,
                                        IntPredicate isQuestItem,
                                        boolean untradeableItemsTradeable) {
        return AgentInventoryItemPolicy.collectNamedItems(
                agent,
                fragment,
                AgentItemQueryNormalizer::normalize,
                normalizedItemName,
                isQuestItem,
                untradeableItemsTradeable);
    }

    public static String normalizedItemName(int itemId, InventoryGateway inventory) {
        return normalizedItemNameCache.computeIfAbsent(itemId, id -> loadNormalizedItemName(id, inventory));
    }

    public static boolean itemNameContains(int itemId, String normalizedFragment, InventoryGateway inventory) {
        String name = normalizedItemName(itemId, inventory);
        return name != null && name.contains(normalizedFragment);
    }

    public static String normalizeQuery(String text) {
        return AgentItemQueryNormalizer.normalize(text);
    }

    private static String loadNormalizedItemName(int itemId, InventoryGateway inventory) {
        String name = inventory.getItemName(itemId);
        return name != null ? AgentItemQueryNormalizer.normalize(name) : "";
    }
}
