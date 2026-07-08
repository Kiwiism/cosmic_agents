package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentPendingTradeStateRuntime;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPendingTradeStateRuntimeTest {
    @Test
    void adaptsPendingTradeActiveGuard() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentPendingTradeStateRuntime.hasActiveSequence(entry));
        assertTrue(AgentPendingTradeStateRuntime.isIdle(entry));

        AgentPendingTradeStateRuntime.setCategory(entry, "trash");

        assertTrue(AgentPendingTradeStateRuntime.hasActiveSequence(entry));
        assertFalse(AgentPendingTradeStateRuntime.isIdle(entry));
        assertEquals("trash", AgentPendingTradeStateRuntime.category(entry));
        assertTrue(AgentPendingTradeStateRuntime.isCategory(entry, "trash"));
        assertFalse(AgentPendingTradeStateRuntime.isSupplyShareCategory(entry));

        AgentPendingTradeStateRuntime.setCategory(entry, "pot_share");

        assertTrue(AgentPendingTradeStateRuntime.isSupplyShareCategory(entry));

        AgentPendingTradeStateRuntime.clearCategory(entry);

        assertFalse(AgentPendingTradeStateRuntime.hasActiveSequence(entry));
    }

    @Test
    void adaptsQueuedTradeRetryState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AtomicBoolean firstRan = new AtomicBoolean(false);
        AtomicBoolean secondRan = new AtomicBoolean(false);
        Runnable first = () -> firstRan.set(true);
        Runnable second = () -> secondRan.set(true);

        AgentPendingTradeStateRuntime.queueRetry(entry, first, 10_000);
        AgentPendingTradeStateRuntime.queueRetry(entry, second, 5_000);

        assertTrue(AgentPendingTradeStateRuntime.hasQueuedRetry(entry));
        assertEquals(10_000, AgentPendingTradeStateRuntime.retryDelayMs(entry));

        AgentPendingTradeStateRuntime.setRetryDelayMs(entry, 0);
        Runnable retry = AgentPendingTradeStateRuntime.takeRetry(entry);

        assertSame(first, retry);
        assertFalse(AgentPendingTradeStateRuntime.hasQueuedRetry(entry));
        retry.run();
        assertTrue(firstRan.get());
        assertFalse(secondRan.get());
    }

    @Test
    void adaptsShareBudgetCapState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertEquals((short) 5000, AgentPendingTradeStateRuntime.capShareQuantity(entry, (short) 5000));

        AgentPendingTradeStateRuntime.setShareBudget(entry, 2250);

        assertEquals(2250, AgentPendingTradeStateRuntime.shareBudget(entry));
        assertEquals((short) 2250, AgentPendingTradeStateRuntime.capShareQuantity(entry, (short) 5000));
        assertEquals(0, AgentPendingTradeStateRuntime.shareBudget(entry));

        AgentPendingTradeStateRuntime.setShareBudget(entry, 1000);
        AgentPendingTradeStateRuntime.clearShareBudget(entry);

        assertEquals(0, AgentPendingTradeStateRuntime.shareBudget(entry));
    }

    @Test
    void adaptsCategoryMessageState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertNull(AgentPendingTradeStateRuntime.categoryMessage(entry));

        AgentPendingTradeStateRuntime.setCategoryMessage(entry, "trading equips");

        assertEquals("trading equips", AgentPendingTradeStateRuntime.categoryMessage(entry));
        assertEquals("trading equips", AgentPendingTradeStateRuntime.takeCategoryMessage(entry));
        assertNull(AgentPendingTradeStateRuntime.categoryMessage(entry));

        AgentPendingTradeStateRuntime.setCategoryMessage(entry, "trading ammo");
        AgentPendingTradeStateRuntime.clearCategoryMessage(entry);

        assertNull(AgentPendingTradeStateRuntime.categoryMessage(entry));
    }

    @Test
    void adaptsRecipientIdState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertEquals(0, AgentPendingTradeStateRuntime.recipientId(entry));

        AgentPendingTradeStateRuntime.setRecipientId(entry, 1234);

        assertEquals(1234, AgentPendingTradeStateRuntime.recipientId(entry));

        AgentPendingTradeStateRuntime.clearRecipientId(entry);

        assertEquals(0, AgentPendingTradeStateRuntime.recipientId(entry));
    }

    @Test
    void adaptsInviteAnnouncedState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentPendingTradeStateRuntime.inviteAnnounced(entry));

        AgentPendingTradeStateRuntime.markInviteAnnounced(entry);

        assertTrue(AgentPendingTradeStateRuntime.inviteAnnounced(entry));

        AgentPendingTradeStateRuntime.clearInviteAnnounced(entry);

        assertFalse(AgentPendingTradeStateRuntime.inviteAnnounced(entry));
    }

    @Test
    void adaptsTimerState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertEquals(0, AgentPendingTradeStateRuntime.timerMs(entry));

        AgentPendingTradeStateRuntime.setTimerMs(entry, 1_000);
        AgentPendingTradeStateRuntime.addTimerMs(entry, 250);

        assertEquals(1_250, AgentPendingTradeStateRuntime.timerMs(entry));

        AgentPendingTradeStateRuntime.tickTimerDown(entry, value -> value - 100);

        assertEquals(1_150, AgentPendingTradeStateRuntime.timerMs(entry));

        AgentPendingTradeStateRuntime.clearTimer(entry);

        assertEquals(0, AgentPendingTradeStateRuntime.timerMs(entry));
    }

    @Test
    void adaptsSingleBatchState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentPendingTradeStateRuntime.singleBatch(entry));

        AgentPendingTradeStateRuntime.setSingleBatch(entry, true);

        assertTrue(AgentPendingTradeStateRuntime.singleBatch(entry));

        AgentPendingTradeStateRuntime.clearSingleBatch(entry);

        assertFalse(AgentPendingTradeStateRuntime.singleBatch(entry));
    }

    @Test
    void adaptsMesoState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertEquals(0, AgentPendingTradeStateRuntime.meso(entry));
        assertFalse(AgentPendingTradeStateRuntime.mesoAdded(entry));
        assertFalse(AgentPendingTradeStateRuntime.hasMesoToAdd(entry));

        AgentPendingTradeStateRuntime.setMeso(entry, 12_345);

        assertEquals(12_345, AgentPendingTradeStateRuntime.meso(entry));
        assertTrue(AgentPendingTradeStateRuntime.hasMesoToAdd(entry));

        AgentPendingTradeStateRuntime.markMesoAdded(entry);

        assertTrue(AgentPendingTradeStateRuntime.mesoAdded(entry));
        assertFalse(AgentPendingTradeStateRuntime.hasMesoToAdd(entry));

        AgentPendingTradeStateRuntime.clearMeso(entry);
        AgentPendingTradeStateRuntime.clearMesoAdded(entry);

        assertEquals(0, AgentPendingTradeStateRuntime.meso(entry));
        assertFalse(AgentPendingTradeStateRuntime.mesoAdded(entry));
    }

    @Test
    void adaptsCompletionFlags() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentPendingTradeStateRuntime.allItemsAdded(entry));
        assertFalse(AgentPendingTradeStateRuntime.botDone(entry));

        AgentPendingTradeStateRuntime.markAllItemsAdded(entry);
        AgentPendingTradeStateRuntime.markBotDone(entry);

        assertTrue(AgentPendingTradeStateRuntime.allItemsAdded(entry));
        assertTrue(AgentPendingTradeStateRuntime.botDone(entry));

        AgentPendingTradeStateRuntime.clearAllItemsAdded(entry);
        AgentPendingTradeStateRuntime.clearBotDone(entry);

        assertFalse(AgentPendingTradeStateRuntime.allItemsAdded(entry));
        assertFalse(AgentPendingTradeStateRuntime.botDone(entry));
    }

    @Test
    void adaptsItemIndexState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertEquals(0, AgentPendingTradeStateRuntime.itemIndex(entry));

        AgentPendingTradeStateRuntime.incrementItemIndex(entry);
        AgentPendingTradeStateRuntime.incrementItemIndex(entry);

        assertEquals(2, AgentPendingTradeStateRuntime.itemIndex(entry));

        AgentPendingTradeStateRuntime.clearItemIndex(entry);

        assertEquals(0, AgentPendingTradeStateRuntime.itemIndex(entry));
    }

    @Test
    void adaptsItemListState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        List<Item> items = List.of(new Item(2000000, (short) 1, (short) 2));

        assertTrue(AgentPendingTradeStateRuntime.isBetweenBatches(entry));

        AgentPendingTradeStateRuntime.setItems(entry, items);

        assertSame(items, AgentPendingTradeStateRuntime.items(entry));
        assertFalse(AgentPendingTradeStateRuntime.isBetweenBatches(entry));

        AgentPendingTradeStateRuntime.clearItems(entry);

        assertTrue(AgentPendingTradeStateRuntime.isBetweenBatches(entry));
    }

    @Test
    void adaptsRestoreSlotState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Item original = new Item(1040000, (short) 1, (short) 1);
        Item tradeCopy = original.copy();

        assertFalse(AgentPendingTradeStateRuntime.hasRestoreSlots(entry));

        AgentPendingTradeStateRuntime.rememberRestoreSlot(entry, original, (short) -5);

        assertTrue(AgentPendingTradeStateRuntime.hasRestoreSlots(entry));

        AgentPendingTradeStateRuntime.transferRestoreSlot(entry, original, tradeCopy);

        assertEquals(1, AgentPendingTradeStateRuntime.restoreSlotEntries(entry).size());
        assertSame(tradeCopy, AgentPendingTradeStateRuntime.restoreSlotEntries(entry).get(0).getKey());
        assertEquals((short) -5, AgentPendingTradeStateRuntime.restoreSlotEntries(entry).get(0).getValue());

        AgentPendingTradeStateRuntime.clearRestoreSlots(entry);

        assertFalse(AgentPendingTradeStateRuntime.hasRestoreSlots(entry));
    }

    @Test
    void adaptsOwnerGivenItemState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Item item = new Item(1040000, (short) 1, (short) 1);

        assertFalse(AgentPendingTradeStateRuntime.hasOwnerGivenItems(entry));

        AgentPendingTradeStateRuntime.addOwnerGivenItem(entry, item);

        assertTrue(AgentPendingTradeStateRuntime.hasOwnerGivenItems(entry));

        AgentPendingTradeStateRuntime.clearOwnerGivenItems(entry);

        assertFalse(AgentPendingTradeStateRuntime.hasOwnerGivenItems(entry));
    }
}
