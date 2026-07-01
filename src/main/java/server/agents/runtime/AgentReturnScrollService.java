package server.agents.runtime;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.manipulator.InventoryManipulator;
import server.ItemInformationProvider;
import server.StatEffect;

/**
 * Agent runtime helper for using a Return Scroll - Nearest Town.
 */
public final class AgentReturnScrollService {
    private static final int RETURN_SCROLL_NEAREST_TOWN = 2030000;

    private AgentReturnScrollService() {
    }

    public static boolean tryUseNearestTownReturnScroll(Character agent) {
        var use = agent.getInventory(InventoryType.USE);
        if (use == null) {
            return false;
        }
        for (Item item : use.list()) {
            if (item == null || item.getQuantity() <= 0) {
                continue;
            }
            if (item.getItemId() != RETURN_SCROLL_NEAREST_TOWN) {
                continue;
            }
            StatEffect effect;
            try {
                effect = ItemInformationProvider.getInstance().getItemEffect(RETURN_SCROLL_NEAREST_TOWN);
            } catch (Exception e) {
                return false;
            }
            if (effect == null || !effect.applyTo(agent)) {
                return false;
            }
            InventoryManipulator.removeFromSlot(
                    agent.getClient(), InventoryType.USE, item.getPosition(), (short) 1, false);
            return true;
        }
        return false;
    }
}
