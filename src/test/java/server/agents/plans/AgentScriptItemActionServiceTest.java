package server.agents.plans;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;
import server.agents.integration.InventoryGateway;
import server.agents.runtime.AgentRuntimeEntry;
import testutil.Items;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentScriptItemActionServiceTest {
    @Test
    void returnsFalseWhenAgentOrTypeIsMissing() {
        AgentRuntimeEntry missingAgent = new AgentRuntimeEntry(null, mock(Character.class), null);

        assertFalse(AgentScriptItemActionService.dropItem(missingAgent, InventoryType.ETC, 4000000, (short) 1));

        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);

        assertFalse(AgentScriptItemActionService.dropItem(entry, null, 4000000, (short) 1));
    }

    @Test
    void returnsFalseWhenInventoryOrItemIsMissing() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        when(agent.getInventory(InventoryType.ETC)).thenReturn(null);

        assertFalse(AgentScriptItemActionService.dropItem(entry, InventoryType.ETC, 4000000, (short) 1));

        Inventory inventory = new Inventory(agent, InventoryType.ETC, (byte) 8);
        inventory.addItem(Items.itemWithQuantity(4000001, 1));
        when(agent.getInventory(InventoryType.ETC)).thenReturn(inventory);

        assertFalse(AgentScriptItemActionService.dropItem(entry, InventoryType.ETC, 4000000, (short) 1));
    }

    @Test
    void dropsRequestedQuantityThroughInventoryGateway() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        Inventory inventory = new Inventory(agent, InventoryType.ETC, (byte) 8);
        inventory.addItem(Items.itemWithQuantity(4000000, 7));
        when(agent.getInventory(InventoryType.ETC)).thenReturn(inventory);
        InventoryGateway gateway = mock(InventoryGateway.class);

        assertTrue(AgentScriptItemActionService.dropItem(entry, InventoryType.ETC, 4000000, (short) 3, gateway));

        verify(gateway).dropItem(agent, InventoryType.ETC, (short) 1, (short) 3);
    }

    @Test
    void dropsAvailableQuantityWhenRequestedQuantityIsNotPositiveOrTooLarge() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        Inventory inventory = new Inventory(agent, InventoryType.ETC, (byte) 8);
        inventory.addItem(Items.itemWithQuantity(4000000, 7));
        when(agent.getInventory(InventoryType.ETC)).thenReturn(inventory);
        InventoryGateway gateway = mock(InventoryGateway.class);

        assertTrue(AgentScriptItemActionService.dropItem(entry, InventoryType.ETC, 4000000, (short) 0, gateway));
        verify(gateway).dropItem(agent, InventoryType.ETC, (short) 1, (short) 7);

        gateway = mock(InventoryGateway.class);
        assertTrue(AgentScriptItemActionService.dropItem(entry, InventoryType.ETC, 4000000, (short) 99, gateway));
        verify(gateway).dropItem(agent, InventoryType.ETC, (short) 1, (short) 7);
    }
}
