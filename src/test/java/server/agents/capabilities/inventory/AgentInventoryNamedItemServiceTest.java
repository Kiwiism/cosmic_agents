package server.agents.capabilities.inventory;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.integration.InventoryGateway;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentInventoryNamedItemServiceTest {
    @Test
    void collectNamedItemsUsesNormalizedNamesAndSkipsQuestItems() {
        Character agent = mock(Character.class);
        Inventory equip = oneSlotInventory();
        Inventory use = oneSlotInventory();
        Inventory etc = oneSlotInventory();
        Inventory setup = oneSlotInventory();
        Item redSword = item(4001, InventoryType.EQUIP);
        Item bluePotion = item(4002, InventoryType.USE);
        Item redQuest = item(4003, InventoryType.ETC);
        Item redChair = item(4004, InventoryType.SETUP);

        when(agent.getInventory(InventoryType.EQUIP)).thenReturn(equip);
        when(agent.getInventory(InventoryType.USE)).thenReturn(use);
        when(agent.getInventory(InventoryType.ETC)).thenReturn(etc);
        when(agent.getInventory(InventoryType.SETUP)).thenReturn(setup);
        when(equip.getItem((short) 1)).thenReturn(redSword);
        when(use.getItem((short) 1)).thenReturn(bluePotion);
        when(etc.getItem((short) 1)).thenReturn(redQuest);
        when(setup.getItem((short) 1)).thenReturn(redChair);

        List<Item> result = AgentInventoryNamedItemService.collectNamedItems(agent, "reds",
                itemId -> switch (itemId) {
                    case 4001 -> "red sword";
                    case 4002 -> "blue potion";
                    case 4003 -> "red quest item";
                    case 4004 -> "red chair";
                    default -> "";
                },
                itemId -> itemId == 4003,
                false);

        assertEquals(List.of(redSword, redChair), result);
    }

    @Test
    void publicCollectionUsesInventoryGatewayForNamesAndQuestItems() {
        Character agent = mock(Character.class);
        Inventory equip = oneSlotInventory();
        Inventory use = oneSlotInventory();
        Inventory etc = oneSlotInventory();
        Inventory setup = oneSlotInventory();
        InventoryGateway inventoryGateway = mock(InventoryGateway.class);
        Item redSword = item(4101, InventoryType.EQUIP);
        Item bluePotion = item(4102, InventoryType.USE);
        Item redQuest = item(4103, InventoryType.ETC);
        Item redChair = item(4104, InventoryType.SETUP);

        when(agent.getInventory(InventoryType.EQUIP)).thenReturn(equip);
        when(agent.getInventory(InventoryType.USE)).thenReturn(use);
        when(agent.getInventory(InventoryType.ETC)).thenReturn(etc);
        when(agent.getInventory(InventoryType.SETUP)).thenReturn(setup);
        when(equip.getItem((short) 1)).thenReturn(redSword);
        when(use.getItem((short) 1)).thenReturn(bluePotion);
        when(etc.getItem((short) 1)).thenReturn(redQuest);
        when(setup.getItem((short) 1)).thenReturn(redChair);
        when(inventoryGateway.getItemName(4101)).thenReturn("Red Sword");
        when(inventoryGateway.getItemName(4102)).thenReturn("Blue Potion");
        when(inventoryGateway.getItemName(4103)).thenReturn("Red Quest Item");
        when(inventoryGateway.getItemName(4104)).thenReturn("Red Chair");
        when(inventoryGateway.isQuestItem(4103)).thenReturn(true);

        List<Item> result = AgentInventoryNamedItemService.collectNamedItems(agent, "reds", inventoryGateway);

        assertEquals(List.of(redSword, redChair), result);
        verify(inventoryGateway).isQuestItem(4103);
    }

    @Test
    void normalizeQueryMatchesLegacySingularization() {
        assertEquals("red potion", AgentInventoryNamedItemService.normalizeQuery("Red potions?!"));
    }

    private static Inventory oneSlotInventory() {
        Inventory inventory = mock(Inventory.class);
        when(inventory.getSlotLimit()).thenReturn((byte) 1);
        return inventory;
    }

    private static Item item(int itemId, InventoryType type) {
        Item item = mock(Item.class);
        when(item.getItemId()).thenReturn(itemId);
        when(item.getInventoryType()).thenReturn(type);
        return item;
    }
}
