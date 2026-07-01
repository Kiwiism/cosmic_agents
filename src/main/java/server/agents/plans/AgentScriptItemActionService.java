package server.agents.plans;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.manipulator.InventoryManipulator;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;

public final class AgentScriptItemActionService {
    private AgentScriptItemActionService() {
    }

    public static boolean dropItem(BotEntry entry, InventoryType type, int itemId, short quantity) {
        Character agent = AgentBotRuntimeIdentityRuntime.bot(entry);
        if (agent == null || type == null) {
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
        InventoryManipulator.drop(agent.getClient(), type, item.getPosition(), dropQuantity);
        return true;
    }
}
