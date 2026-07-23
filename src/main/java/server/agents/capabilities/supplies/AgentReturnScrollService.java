package server.agents.capabilities.supplies;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import server.StatEffect;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.InventoryGateway;

/**
 * Agent supply helper for using a Return Scroll - Nearest Town.
 */
public final class AgentReturnScrollService {
    private static final int RETURN_SCROLL_NEAREST_TOWN = config.AgentTuning.intValue("server.agents.capabilities.supplies.AgentReturnScrollService.RETURN_SCROLL_NEAREST_TOWN");

    private AgentReturnScrollService() {
    }

    public static boolean tryUseNearestTownReturnScroll(Character agent) {
        return tryUseNearestTownReturnScroll(agent, AgentInventoryGatewayRuntime.inventory());
    }

    static boolean tryUseNearestTownReturnScroll(Character agent, InventoryGateway inventoryGateway) {
        if (agent == null || inventoryGateway == null) {
            return false;
        }
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
                effect = inventoryGateway.getItemEffect(RETURN_SCROLL_NEAREST_TOWN);
            } catch (Exception e) {
                return false;
            }
            if (effect == null || !effect.applyTo(agent)) {
                return false;
            }
            inventoryGateway.removeFromSlot(agent, InventoryType.USE, item.getPosition(), (short) 1, false);
            return true;
        }
        return false;
    }
}
