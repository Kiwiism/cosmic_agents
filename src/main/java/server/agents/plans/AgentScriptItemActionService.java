package server.agents.plans;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.InventoryGateway;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.capabilities.inventory.AgentInventoryReservationRuntime;
import server.agents.runtime.AgentRuntimeEntry;

public final class AgentScriptItemActionService {
    private AgentScriptItemActionService() {
    }

    public static boolean dropItem(AgentRuntimeEntry entry, InventoryType type, int itemId, short quantity) {
        return dropItem(entry, type, itemId, quantity, AgentInventoryGatewayRuntime.inventory());
    }

    public static boolean dropMesos(AgentRuntimeEntry entry, int amount) {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null || agent.getMap() == null || amount < 10 || amount > agent.getMeso()) {
            return false;
        }
        agent.gainMeso(-amount, false, true, false);
        agent.getMap().spawnMesoDrop(amount, agent.getPosition(), agent, agent,
                true, (byte) 2, (short) 0);
        return true;
    }

    static boolean dropItem(AgentRuntimeEntry entry, InventoryType type, int itemId, short quantity,
                            InventoryGateway inventoryGateway) {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null || type == null || inventoryGateway == null) {
            return false;
        }
        var inventory = agent.getInventory(type);
        if (inventory == null) {
            return false;
        }
        Item item = inventory.findById(itemId);
        if (item == null || item.getQuantity() <= 0
                || !AgentInventoryReservationRuntime.mayConsume(
                entry, item, System.currentTimeMillis())) {
            return false;
        }
        short dropQuantity = quantity <= 0 ? item.getQuantity() : (short) Math.min(quantity, item.getQuantity());
        inventoryGateway.dropItem(agent, type, item.getPosition(), dropQuantity);
        return true;
    }
}
