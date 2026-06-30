package server.agents.capabilities.inventory;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.IntFunction;
import java.util.function.Predicate;

public final class AgentInventoryItemPolicy {
    private AgentInventoryItemPolicy() {
    }

    public static boolean hasItem(Character agent, Item item) {
        if (agent == null || item == null) {
            return false;
        }

        Inventory inventory = agent.getInventory(item.getInventoryType());
        if (inventory == null) {
            return false;
        }

        Item current = inventory.getItem(item.getPosition());
        return current == item;
    }

    public static boolean isSafeToDrop(Item item, IntPredicate isQuestItem, boolean untradeableItemsTradeable) {
        if (item.isUntradeable() && !untradeableItemsTradeable) return false;
        if (isQuestItem.test(item.getItemId())) return false;
        return true;
    }

    public static List<Item> collectSafeItems(Character agent,
                                              InventoryType type,
                                              Predicate<Item> filter,
                                              IntPredicate isQuestItem,
                                              boolean untradeableItemsTradeable) {
        List<Item> result = new ArrayList<>();
        Inventory inventory = agent.getInventory(type);
        for (short slot = 1; slot <= inventory.getSlotLimit(); slot++) {
            Item item = inventory.getItem(slot);
            if (item != null
                    && isSafeToDrop(item, isQuestItem, untradeableItemsTradeable)
                    && filter.test(item)) {
                result.add(item);
            }
        }
        return result;
    }

    public static List<Short> selectSafeDropSlots(Character agent,
                                                  InventoryType type,
                                                  Predicate<Item> filter,
                                                  IntPredicate isQuestItem,
                                                  boolean untradeableItemsTradeable) {
        List<Short> slots = new ArrayList<>();
        Inventory inventory = agent.getInventory(type);
        for (short slot = 1; slot <= inventory.getSlotLimit(); slot++) {
            Item item = inventory.getItem(slot);
            if (item != null
                    && isSafeToDrop(item, isQuestItem, untradeableItemsTradeable)
                    && filter.test(item)) {
                slots.add(slot);
            }
        }
        return slots;
    }

    public static List<Item> collectNamedItems(Character agent,
                                               String fragment,
                                               Function<String, String> normalizer,
                                               IntFunction<String> normalizedItemName,
                                               IntPredicate isQuestItem,
                                               boolean untradeableItemsTradeable) {
        List<Item> result = new ArrayList<>();
        String normalizedFragment = normalizer.apply(fragment);
        for (InventoryType type : List.of(
                InventoryType.EQUIP, InventoryType.USE, InventoryType.ETC, InventoryType.SETUP)) {
            result.addAll(collectSafeItems(agent, type, item -> {
                String name = normalizedItemName.apply(item.getItemId());
                return name != null && name.contains(normalizedFragment);
            }, isQuestItem, untradeableItemsTradeable));
        }
        return result;
    }
}
