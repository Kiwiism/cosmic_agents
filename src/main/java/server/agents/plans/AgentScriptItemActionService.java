package server.agents.plans;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.InventoryGateway;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

public final class AgentScriptItemActionService {
    private AgentScriptItemActionService() {
    }

    public static boolean dropItem(AgentRuntimeEntry entry, InventoryType type, int itemId, short quantity) {
        return dropItem(entry, type, itemId, quantity, AgentInventoryGatewayRuntime.inventory());
    }

    static boolean dropItem(AgentRuntimeEntry entry, InventoryType type, int itemId, short quantity,
                            InventoryGateway inventoryGateway) {
        if (entry == null || entry.isPartnerManaged()) {
            return false;
        }
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null || type == null || inventoryGateway == null) {
            return false;
        }
        var inventory = agent.getInventory(type);
        if (inventory == null) {
            return false;
        }
        Item item = inventory.findById(itemId);
        if (item == null || item.getQuantity() <= 0) {
            return false;
        }
        short dropQuantity = quantity <= 0 ? item.getQuantity() : (short) Math.min(quantity, item.getQuantity());
        inventoryGateway.dropItem(agent, type, item.getPosition(), dropQuantity);
        return true;
    }
}
