package server.agents.capabilities.equipment;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentEquipmentUnequipServiceTest {
    @Test
    void unequipAllReturnsLegacyEmptyMessage() {
        Character agent = mock(Character.class);
        Inventory equip = mock(Inventory.class);
        Inventory equipped = mock(Inventory.class);
        AgentEquipmentUnequipService.UnequipHooks hooks = mock(AgentEquipmentUnequipService.UnequipHooks.class);
        when(agent.getInventory(InventoryType.EQUIP)).thenReturn(equip);
        when(agent.getInventory(InventoryType.EQUIPPED)).thenReturn(equipped);
        when(equipped.list()).thenReturn(List.of());

        assertEquals("nothing to unequip", AgentEquipmentUnequipService.unequipAll(agent, hooks));
        verifyNoInteractions(hooks);
    }

    @Test
    void unequipAllPreservesLegacyFreeSlotGuard() {
        Character agent = mock(Character.class);
        Inventory equip = mock(Inventory.class);
        Inventory equipped = mock(Inventory.class);
        Item first = item(1001, (short) -1);
        Item second = item(1002, (short) -2);
        AgentEquipmentUnequipService.UnequipHooks hooks = mock(AgentEquipmentUnequipService.UnequipHooks.class);
        when(agent.getInventory(InventoryType.EQUIP)).thenReturn(equip);
        when(agent.getInventory(InventoryType.EQUIPPED)).thenReturn(equipped);
        when(equipped.list()).thenReturn(List.of(first, second));
        when(equip.getNumFreeSlot()).thenReturn((short) 1);

        assertEquals("need 2 free equip slots, only have 1",
                AgentEquipmentUnequipService.unequipAll(agent, hooks));
    }

    @Test
    void unequipSlotMovesSelectedNonCashSlotsAndFormatsNames() {
        Character agent = mock(Character.class);
        Inventory equip = mock(Inventory.class);
        Inventory equipped = mock(Inventory.class);
        Item hat = item(1002001, (short) -1);
        Item cashCape = item(1102001, (short) -9);
        AgentEquipmentUnequipService.UnequipHooks hooks = mock(AgentEquipmentUnequipService.UnequipHooks.class);
        when(agent.getInventory(InventoryType.EQUIP)).thenReturn(equip);
        when(agent.getInventory(InventoryType.EQUIPPED)).thenReturn(equipped);
        when(equipped.getItem((short) -1)).thenReturn(hat);
        when(equipped.getItem((short) -9)).thenReturn(cashCape);
        when(hooks.isCash(1102001)).thenReturn(true);
        when(equip.getNumFreeSlot()).thenReturn((short) 1);
        when(equip.getNextFreeSlot()).thenReturn((short) 4);
        when(hooks.itemName(1002001)).thenReturn("Blue Bandana");

        assertEquals("unequipped Blue Bandana",
                AgentEquipmentUnequipService.unequipSlot(agent, new short[]{-1, -9}, hooks));
        verify(hooks).move(agent, (short) -1, (short) 4);
    }

    private static Item item(int itemId, short position) {
        Item item = mock(Item.class);
        when(item.getItemId()).thenReturn(itemId);
        when(item.getPosition()).thenReturn(position);
        return item;
    }
}
