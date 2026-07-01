package server.agents.plans;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;
import server.bots.BotEntry;
import testutil.Items;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentScriptItemActionServiceTest {
    @Test
    void returnsFalseWhenAgentOrTypeIsMissing() {
        BotEntry missingAgent = new BotEntry(null, mock(Character.class), null);

        assertFalse(AgentScriptItemActionService.dropItem(missingAgent, InventoryType.ETC, 4000000, (short) 1));

        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);

        assertFalse(AgentScriptItemActionService.dropItem(entry, null, 4000000, (short) 1));
    }

    @Test
    void returnsFalseWhenInventoryOrItemIsMissing() {
        Character agent = mock(Character.class);
        BotEntry entry = new BotEntry(agent, mock(Character.class), null);
        when(agent.getInventory(InventoryType.ETC)).thenReturn(null);

        assertFalse(AgentScriptItemActionService.dropItem(entry, InventoryType.ETC, 4000000, (short) 1));

        Inventory inventory = new Inventory(agent, InventoryType.ETC, (byte) 8);
        inventory.addItem(Items.itemWithQuantity(4000001, 1));
        when(agent.getInventory(InventoryType.ETC)).thenReturn(inventory);

        assertFalse(AgentScriptItemActionService.dropItem(entry, InventoryType.ETC, 4000000, (short) 1));
    }
}
