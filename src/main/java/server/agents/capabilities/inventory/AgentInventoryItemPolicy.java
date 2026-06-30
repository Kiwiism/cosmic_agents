package server.agents.capabilities.inventory;

import client.Character;
import client.inventory.Inventory;
import client.inventory.Item;

import java.util.function.IntPredicate;

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
}
