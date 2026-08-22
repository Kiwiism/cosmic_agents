package server.agents.capabilities.supplies;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.inventory.ItemConstants;
import server.agents.capabilities.contracts.AgentResourceCategory;

/** Shared live quantities used by planning, procurement reconciliation, and diagnostics. */
public final class AgentSupplyInventorySnapshot {
    private AgentSupplyInventorySnapshot() {
    }

    public static int quantity(Character agent, AgentResourceCategory category) {
        if (agent == null || category == null) return 0;
        if (agent.getInventory(InventoryType.USE) == null) return 0;
        if (category == AgentResourceCategory.HP_POTION
                || category == AgentResourceCategory.MP_POTION) {
            int[] potions = AgentPotionService.countPotions(agent);
            return category == AgentResourceCategory.HP_POTION ? potions[0] : potions[1];
        }
        int total = 0;
        for (Item item : agent.getInventory(InventoryType.USE).list()) {
            if (matches(item.getItemId(), category)) total += Math.max(0, item.getQuantity());
        }
        return total;
    }

    private static boolean matches(int itemId, AgentResourceCategory category) {
        return switch (category) {
            case ARROW -> ItemConstants.isArrowForBow(itemId);
            case CROSSBOW_BOLT -> ItemConstants.isArrowForCrossBow(itemId);
            case THROWING_STAR -> ItemConstants.isThrowingStar(itemId);
            case BULLET -> ItemConstants.isBullet(itemId);
            default -> false;
        };
    }
}
