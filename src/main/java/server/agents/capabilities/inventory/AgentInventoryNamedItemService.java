package server.agents.capabilities.inventory;

import client.Character;
import client.inventory.Item;
import config.YamlConfig;
import server.ItemInformationProvider;
import server.agents.capabilities.dialogue.AgentItemQueryNormalizer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

public final class AgentInventoryNamedItemService {
    private static final Map<Integer, String> normalizedItemNameCache = new ConcurrentHashMap<>();

    private AgentInventoryNamedItemService() {
    }

    public static int countNamedItems(Character agent, String fragment) {
        return AgentInventoryTradePolicy.itemQuantitySum(collectNamedItems(agent, fragment));
    }

    public static List<Item> collectNamedItems(Character agent, String fragment) {
        return collectNamedItems(
                agent,
                fragment,
                AgentInventoryNamedItemService::normalizedItemName,
                ItemInformationProvider.getInstance()::isQuestItem,
                YamlConfig.config.server.UNTRADEABLE_ITEMS_TRADEABLE);
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

    public static String normalizedItemName(int itemId) {
        return normalizedItemNameCache.computeIfAbsent(itemId, AgentInventoryNamedItemService::loadNormalizedItemName);
    }

    public static boolean itemNameContains(int itemId, String normalizedFragment) {
        String name = normalizedItemName(itemId);
        return name != null && name.contains(normalizedFragment);
    }

    public static String normalizeQuery(String text) {
        return AgentItemQueryNormalizer.normalize(text);
    }

    private static String loadNormalizedItemName(int itemId) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        String name;
        synchronized (ii) {
            name = ii.getName(itemId);
        }
        return name != null ? AgentItemQueryNormalizer.normalize(name) : "";
    }
}
