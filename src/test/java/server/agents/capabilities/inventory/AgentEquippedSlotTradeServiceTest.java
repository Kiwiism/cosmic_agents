package server.agents.capabilities.inventory;

import server.agents.capabilities.trade.AgentPendingTradeStateRuntime;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.integration.InventoryGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentEquippedSlotTradeServiceTest {
    @Test
    void countsEquippedSlotItemsIgnoringCashItems() {
        Character agent = mock(Character.class);
        Inventory equipped = mock(Inventory.class);
        Item cashHat = item(1000);
        Item glove = item(1001);
        InventoryGateway inventory = inventoryGateway();
        when(inventory.isCashItem(1000)).thenReturn(true);

        when(agent.getInventory(InventoryType.EQUIPPED)).thenReturn(equipped);
        when(equipped.getItem((short) -1)).thenReturn(cashHat);
        when(equipped.getItem((short) -10)).thenReturn(glove);

        int count = AgentEquippedSlotTradeService.countEquippedSlotItems(
                agent,
                "style",
                ignored -> new short[]{-1, -10},
                inventory);

        assertEquals(1, count);
        assertTrue(AgentEquippedSlotTradeService.hasEquippedSlotItems(
                agent,
                "style",
                ignored -> new short[]{-1, -10},
                inventory));
    }

    @Test
    void prepareEquippedSlotTradeItemsMovesSlotsAndRecordsRestoreSlots() {
        Character agent = mock(Character.class);
        Inventory equipped = mock(Inventory.class);
        Inventory equipBag = mock(Inventory.class);
        Item hat = item(1000);
        Item glove = item(1001);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        InventoryGateway inventoryGateway = inventoryGateway();

        when(agent.getInventory(InventoryType.EQUIPPED)).thenReturn(equipped);
        when(agent.getInventory(InventoryType.EQUIP)).thenReturn(equipBag);
        when(equipped.getItem((short) -10)).thenReturn(glove);
        when(equipped.getItem((short) -1)).thenReturn(hat);
        when(equipBag.getNumFreeSlot()).thenReturn((short) 2);
        when(equipBag.getNextFreeSlot()).thenReturn((short) 1, (short) 2);
        when(equipBag.getItem((short) 1)).thenReturn(glove);
        when(equipBag.getItem((short) 2)).thenReturn(hat);

        AgentEquippedSlotTradeService.PreparedTradeItems prepared =
                AgentEquippedSlotTradeService.prepareEquippedSlotTradeItems(
                        "style",
                        entry,
                        agent,
                        ignored -> new short[]{-10, -1},
                        inventoryGateway,
                        () -> {
                            throw new AssertionError("restore should not run on success");
                        });

        assertNull(prepared.errorMessage());
        assertEquals(List.of(glove, hat), prepared.items());
        verify(inventoryGateway).moveItem(agent, InventoryType.EQUIP, (short) -10, (short) 1, (short) 1);
        verify(inventoryGateway).moveItem(agent, InventoryType.EQUIP, (short) -1, (short) 2, (short) 1);
        Map<Item, Short> restoreSlots = AgentPendingTradeStateRuntime.restoreSlotEntries(entry).stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertEquals(2, restoreSlots.size());
        assertEquals((short) -10, restoreSlots.get(glove));
        assertEquals((short) -1, restoreSlots.get(hat));
    }

    @Test
    void prepareEquippedSlotTradeItemsReportsFullEquipBagBeforeMoving() {
        Character agent = mock(Character.class);
        Inventory equipped = mock(Inventory.class);
        Inventory equipBag = mock(Inventory.class);
        Item hat = item(1000);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        InventoryGateway inventory = inventoryGateway();
        Runnable restore = mock(Runnable.class);

        when(agent.getInventory(InventoryType.EQUIPPED)).thenReturn(equipped);
        when(agent.getInventory(InventoryType.EQUIP)).thenReturn(equipBag);
        when(equipped.getItem((short) -1)).thenReturn(hat);
        when(equipBag.getNumFreeSlot()).thenReturn((short) 0);

        AgentEquippedSlotTradeService.PreparedTradeItems prepared =
                AgentEquippedSlotTradeService.prepareEquippedSlotTradeItems(
                        "hat",
                        entry,
                        agent,
                        ignored -> new short[]{-1},
                        inventory,
                        restore);

        assertTrue(prepared.items().isEmpty());
        assertEquals("equip bag full", prepared.errorMessage());
        verify(restore, org.mockito.Mockito.never()).run();
        assertFalse(AgentPendingTradeStateRuntime.hasRestoreSlots(entry));
    }

    @Test
    void restoreTemporarilyUnequippedItemsMovesBagItemsBackAndClearsState() {
        Character agent = mock(Character.class);
        Inventory equipped = mock(Inventory.class);
        Inventory equipBag = mock(Inventory.class);
        Item hat = item(1000);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        InventoryGateway inventoryGateway = inventoryGateway();

        when(agent.getInventory(InventoryType.EQUIPPED)).thenReturn(equipped);
        when(agent.getInventory(InventoryType.EQUIP)).thenReturn(equipBag);
        when(equipBag.getItem((short) 1)).thenReturn(hat);
        when(hat.getInventoryType()).thenReturn(InventoryType.EQUIP);
        when(hat.getPosition()).thenReturn((short) 1);
        when(equipped.getItem((short) -1)).thenReturn(null);
        AgentPendingTradeStateRuntime.rememberRestoreSlot(entry, hat, (short) -1);

        AgentEquippedSlotTradeService.restoreTemporarilyUnequippedItems(entry, agent, inventoryGateway);

        verify(inventoryGateway).moveItem(agent, InventoryType.EQUIP, (short) 1, (short) -1, (short) 1);
        assertFalse(AgentPendingTradeStateRuntime.hasRestoreSlots(entry));
    }

    private static Item item(int itemId) {
        Item item = mock(Item.class);
        when(item.getItemId()).thenReturn(itemId);
        return item;
    }

    private static InventoryGateway inventoryGateway() {
        InventoryGateway inventory = mock(InventoryGateway.class);
        when(inventory.isCashItem(org.mockito.ArgumentMatchers.anyInt())).thenReturn(false);
        return inventory;
    }
}

