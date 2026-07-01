package server.agents.capabilities.inventory;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentInventoryCollectionServiceTest {
    @Test
    void collectsSafeBagItemsWithLegacyFilteringInputs() {
        Character agent = mock(Character.class);
        Inventory inventory = mock(Inventory.class);
        Item quest = mock(Item.class);
        Item rejectedByFilter = mock(Item.class);
        Item kept = mock(Item.class);

        when(agent.getInventory(InventoryType.ETC)).thenReturn(inventory);
        when(inventory.getSlotLimit()).thenReturn((byte) 4);
        when(inventory.getItem((short) 1)).thenReturn(quest);
        when(inventory.getItem((short) 2)).thenReturn(null);
        when(inventory.getItem((short) 3)).thenReturn(rejectedByFilter);
        when(inventory.getItem((short) 4)).thenReturn(kept);
        when(quest.getItemId()).thenReturn(2001);
        when(rejectedByFilter.getItemId()).thenReturn(2002);
        when(kept.getItemId()).thenReturn(2003);

        List<Item> items = AgentInventoryCollectionService.collectFromBag(agent, InventoryType.ETC,
                item -> item.getItemId() >= 2003,
                itemId -> itemId == 2001,
                false);

        assertEquals(List.of(kept), items);
    }
}
