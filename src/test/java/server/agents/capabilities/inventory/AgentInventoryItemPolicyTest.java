package server.agents.capabilities.inventory;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentInventoryItemPolicyTest {
    @Test
    void shouldRejectNullAgentOrItem() {
        Character agent = mock(Character.class);
        Item item = mock(Item.class);

        assertFalse(AgentInventoryItemPolicy.hasItem(null, item));
        assertFalse(AgentInventoryItemPolicy.hasItem(agent, null));
    }

    @Test
    void shouldRequireSameItemObjectAtCurrentInventoryPosition() {
        Character agent = mock(Character.class);
        Inventory inventory = mock(Inventory.class);
        Item item = mock(Item.class);
        Item replacement = mock(Item.class);

        when(item.getInventoryType()).thenReturn(InventoryType.USE);
        when(item.getPosition()).thenReturn((short) 3);
        when(agent.getInventory(InventoryType.USE)).thenReturn(inventory);
        when(inventory.getItem((short) 3)).thenReturn(item);

        assertTrue(AgentInventoryItemPolicy.hasItem(agent, item));

        when(inventory.getItem((short) 3)).thenReturn(replacement);
        assertFalse(AgentInventoryItemPolicy.hasItem(agent, item));
    }

    @Test
    void shouldRejectMissingInventory() {
        Character agent = mock(Character.class);
        Item item = mock(Item.class);

        when(item.getInventoryType()).thenReturn(InventoryType.EQUIP);
        when(agent.getInventory(InventoryType.EQUIP)).thenReturn(null);

        assertFalse(AgentInventoryItemPolicy.hasItem(agent, item));
    }

    @Test
    void shouldRejectUnsafeDropItemsLikeLegacyInventory() {
        Item untradeable = mock(Item.class);
        Item questItem = mock(Item.class);
        Item normal = mock(Item.class);

        when(untradeable.isUntradeable()).thenReturn(true);
        when(untradeable.getItemId()).thenReturn(1001);
        when(questItem.getItemId()).thenReturn(1002);
        when(normal.getItemId()).thenReturn(1003);

        assertFalse(AgentInventoryItemPolicy.isSafeToDrop(untradeable, itemId -> itemId == 1002, false));
        assertTrue(AgentInventoryItemPolicy.isSafeToDrop(untradeable, itemId -> itemId == 1002, true));
        assertFalse(AgentInventoryItemPolicy.isSafeToDrop(questItem, itemId -> itemId == 1002, true));
        assertTrue(AgentInventoryItemPolicy.isSafeToDrop(normal, itemId -> itemId == 1002, false));
    }
}
