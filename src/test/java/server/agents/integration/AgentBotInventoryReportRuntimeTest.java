package server.agents.integration;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentBotInventoryReportRuntimeTest {
    @Test
    void slotsReportUsesAgentInventoryFormatter() {
        Character bot = mock(Character.class);
        Inventory equip = inventory(24, 20);
        Inventory use = inventory(24, 21);
        Inventory etc = inventory(24, 22);
        Inventory setup = inventory(24, 23);
        when(bot.getInventory(InventoryType.EQUIP)).thenReturn(equip);
        when(bot.getInventory(InventoryType.USE)).thenReturn(use);
        when(bot.getInventory(InventoryType.ETC)).thenReturn(etc);
        when(bot.getInventory(InventoryType.SETUP)).thenReturn(setup);

        assertEquals("equip: 4/24, use: 3/24, etc: 2/24, setup: 1/24",
                AgentBotInventoryReportRuntime.slotsReport(bot));
    }

    private static Inventory inventory(int slots, int freeSlots) {
        Inventory inventory = mock(Inventory.class);
        when(inventory.getSlotLimit()).thenReturn((byte) slots);
        when(inventory.getNumFreeSlot()).thenReturn((short) freeSlots);
        return inventory;
    }
}
