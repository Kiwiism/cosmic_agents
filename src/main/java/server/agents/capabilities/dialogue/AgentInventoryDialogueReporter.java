package server.agents.capabilities.dialogue;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.inventory.ItemConstants;

public final class AgentInventoryDialogueReporter {
    private AgentInventoryDialogueReporter() {
    }

    public static int countEquipScrolls(Character agent) {
        int count = 0;
        for (Item item : agent.getInventory(InventoryType.USE).list()) {
            int id = item.getItemId();
            if (ItemConstants.isEquipScroll(id)) {
                count += item.getQuantity();
            }
        }
        return count;
    }

    public static String scrollReport(Character agent) {
        return AgentDialogueReportFormatter.scrollCount(countEquipScrolls(agent));
    }
}
