package server.agents.capabilities.inventory;

import client.Character;
import client.Client;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.manipulator.InventoryManipulator;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentEquippedSlotTradeServiceTest {
    @Test
    void countsEquippedSlotItemsIgnoringCashItems() {
        Character agent = mock(Character.class);
        Inventory equipped = mock(Inventory.class);
        Item cashHat = item(1000);
        Item glove = item(1001);
        IntPredicate oldCashItemLookup = AgentEquippedSlotTradeService.cashItemLookup;
        AgentEquippedSlotTradeService.cashItemLookup = itemId -> itemId == 1000;

        try {
            when(agent.getInventory(InventoryType.EQUIPPED)).thenReturn(equipped);
            when(equipped.getItem((short) -1)).thenReturn(cashHat);
            when(equipped.getItem((short) -10)).thenReturn(glove);

            int count = AgentEquippedSlotTradeService.countEquippedSlotItems(
                    agent,
                    "style",
                    ignored -> new short[]{-1, -10});

            assertEquals(1, count);
            assertTrue(AgentEquippedSlotTradeService.hasEquippedSlotItems(
                    agent,
                    "style",
                    ignored -> new short[]{-1, -10}));
        } finally {
            AgentEquippedSlotTradeService.cashItemLookup = oldCashItemLookup;
        }
    }

    @Test
    void prepareEquippedSlotTradeItemsMovesSlotsAndRecordsRestoreSlots() {
        Character agent = mock(Character.class);
        Client client = mock(Client.class);
        Inventory equipped = mock(Inventory.class);
        Inventory equipBag = mock(Inventory.class);
        Item hat = item(1000);
        Item glove = item(1001);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        IntPredicate oldCashItemLookup = AgentEquippedSlotTradeService.cashItemLookup;
        AgentEquippedSlotTradeService.cashItemLookup = itemId -> false;

        try (MockedStatic<InventoryManipulator> inventory = mockStatic(InventoryManipulator.class)) {
            when(agent.getClient()).thenReturn(client);
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
                            () -> {
                                throw new AssertionError("restore should not run on success");
                            });

            assertNull(prepared.errorMessage());
            assertEquals(List.of(glove, hat), prepared.items());
            inventory.verify(() -> InventoryManipulator.handleItemMove(
                    client, InventoryType.EQUIP, (short) -10, (short) 1, (short) 1));
            inventory.verify(() -> InventoryManipulator.handleItemMove(
                    client, InventoryType.EQUIP, (short) -1, (short) 2, (short) 1));
            Map<Item, Short> restoreSlots = AgentBotPendingTradeStateRuntime.restoreSlotEntries(entry).stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            assertEquals(2, restoreSlots.size());
            assertEquals((short) -10, restoreSlots.get(glove));
            assertEquals((short) -1, restoreSlots.get(hat));
        } finally {
            AgentEquippedSlotTradeService.cashItemLookup = oldCashItemLookup;
        }
    }

    @Test
    void prepareEquippedSlotTradeItemsReportsFullEquipBagBeforeMoving() {
        Character agent = mock(Character.class);
        Inventory equipped = mock(Inventory.class);
        Inventory equipBag = mock(Inventory.class);
        Item hat = item(1000);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        IntPredicate oldCashItemLookup = AgentEquippedSlotTradeService.cashItemLookup;
        AgentEquippedSlotTradeService.cashItemLookup = itemId -> false;
        Runnable restore = mock(Runnable.class);

        try {
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
                            restore);

            assertTrue(prepared.items().isEmpty());
            assertEquals("equip bag full", prepared.errorMessage());
            verify(restore, org.mockito.Mockito.never()).run();
            assertFalse(AgentBotPendingTradeStateRuntime.hasRestoreSlots(entry));
        } finally {
            AgentEquippedSlotTradeService.cashItemLookup = oldCashItemLookup;
        }
    }

    @Test
    void restoreTemporarilyUnequippedItemsMovesBagItemsBackAndClearsState() {
        Character agent = mock(Character.class);
        Client client = mock(Client.class);
        Inventory equipped = mock(Inventory.class);
        Inventory equipBag = mock(Inventory.class);
        Item hat = item(1000);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        when(agent.getClient()).thenReturn(client);
        when(agent.getInventory(InventoryType.EQUIPPED)).thenReturn(equipped);
        when(agent.getInventory(InventoryType.EQUIP)).thenReturn(equipBag);
        when(equipBag.getItem((short) 1)).thenReturn(hat);
        when(hat.getInventoryType()).thenReturn(InventoryType.EQUIP);
        when(hat.getPosition()).thenReturn((short) 1);
        when(equipped.getItem((short) -1)).thenReturn(null);
        AgentBotPendingTradeStateRuntime.rememberRestoreSlot(entry, hat, (short) -1);

        try (MockedStatic<InventoryManipulator> inventory = mockStatic(InventoryManipulator.class)) {
            AgentEquippedSlotTradeService.restoreTemporarilyUnequippedItems(entry, agent);

            inventory.verify(() -> InventoryManipulator.handleItemMove(
                    client, InventoryType.EQUIP, (short) 1, (short) -1, (short) 1));
            assertFalse(AgentBotPendingTradeStateRuntime.hasRestoreSlots(entry));
        }
    }

    private static Item item(int itemId) {
        Item item = mock(Item.class);
        when(item.getItemId()).thenReturn(itemId);
        return item;
    }
}

