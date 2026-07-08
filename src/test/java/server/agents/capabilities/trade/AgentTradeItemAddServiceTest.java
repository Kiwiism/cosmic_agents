package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import net.packet.Packet;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import server.Trade;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTradeItemAddServiceTest {
    @Test
    void noRemainingItemReturnsFalseWithoutMutatingIndex() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character agent = mock(Character.class);
        Trade trade = mock(Trade.class);
        AgentTradeStateService.initializeBatch(entry, List.of(), 0);

        boolean handled = AgentTradeItemAddService.addNextItem(
                entry,
                agent,
                trade,
                500,
                (character, type, slot, quantity, fromDrop) -> {},
                (number, item) -> mock(Packet.class));

        assertFalse(handled);
        assertEquals(0, AgentPendingTradeStateRuntime.itemIndex(entry));
    }

    @Test
    void changedSlotStillConsumesAttemptAndSkipsTradeAdd() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character agent = mock(Character.class);
        Trade trade = mock(Trade.class);
        Inventory inventory = mock(Inventory.class);
        Item item = item(2000000, (short) 3, (short) 10);
        AgentTradeStateService.initializeBatch(entry, List.of(item), 0);
        when(agent.getInventory(item.getInventoryType())).thenReturn(inventory);
        when(inventory.getItem((short) 3)).thenReturn(null);

        boolean handled = AgentTradeItemAddService.addNextItem(
                entry,
                agent,
                trade,
                500,
                (character, type, slot, quantity, fromDrop) -> {},
                (number, tradeItem) -> mock(Packet.class));

        assertTrue(handled);
        assertEquals(1, AgentPendingTradeStateRuntime.itemIndex(entry));
        assertEquals(500, AgentPendingTradeStateRuntime.timerMs(entry));
        verify(trade, never()).addItem(any(Item.class));
    }

    @Test
    void successfulAddCopiesItemRemovesInventoryAndSendsPackets() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character agent = mock(Character.class);
        Character recipient = mock(Character.class);
        Trade trade = mock(Trade.class);
        Trade partner = mock(Trade.class);
        Inventory inventory = mock(Inventory.class);
        Packet ownPacket = mock(Packet.class);
        Packet partnerPacket = mock(Packet.class);
        Item item = item(2000000, (short) 3, (short) 10);
        AtomicReference<Item> removedItem = new AtomicReference<>();
        AtomicInteger packetNumberSum = new AtomicInteger();
        AgentTradeStateService.initializeBatch(entry, List.of(item), 0);
        AgentPendingTradeStateRuntime.rememberRestoreSlot(entry, item, (short) -5);

        when(agent.getInventory(InventoryType.USE)).thenReturn(inventory);
        when(inventory.getItem((short) 3)).thenReturn(item);
        when(trade.addItem(any(Item.class))).thenReturn(true);
        when(trade.getPartner()).thenReturn(partner);
        when(partner.getChr()).thenReturn(recipient);

        boolean handled = AgentTradeItemAddService.addNextItem(
                entry,
                agent,
                trade,
                500,
                (character, type, slot, quantity, fromDrop) -> {
                    assertSame(agent, character);
                    assertEquals(InventoryType.USE, type);
                    assertEquals((short) 3, slot);
                    assertEquals((short) 10, quantity);
                    assertFalse(fromDrop);
                },
                (number, tradeItem) -> {
                    packetNumberSum.addAndGet(number);
                    removedItem.set(tradeItem);
                    return number == 0 ? ownPacket : partnerPacket;
                });

        assertTrue(handled);
        ArgumentCaptor<Item> tradeItemCaptor = ArgumentCaptor.forClass(Item.class);
        verify(trade).addItem(tradeItemCaptor.capture());
        Item tradeItem = tradeItemCaptor.getValue();
        assertNotSame(item, tradeItem);
        assertEquals((short) 1, tradeItem.getPosition());
        assertEquals((short) 10, tradeItem.getQuantity());
        assertSame(tradeItem, removedItem.get());
        assertEquals(1, packetNumberSum.get());
        assertEquals(1, AgentPendingTradeStateRuntime.itemIndex(entry));
        assertEquals(500, AgentPendingTradeStateRuntime.timerMs(entry));
        assertSame(tradeItem, AgentPendingTradeStateRuntime.restoreSlotEntries(entry).get(0).getKey());
        assertEquals((short) -5, AgentPendingTradeStateRuntime.restoreSlotEntries(entry).get(0).getValue());
        verify(agent).sendPacket(ownPacket);
        verify(recipient).sendPacket(partnerPacket);
        verify(inventory).lockInventory();
        verify(inventory).unlockInventory();
    }

    private static Item item(int itemId, short position, short quantity) {
        return new Item(itemId, position, quantity);
    }
}
