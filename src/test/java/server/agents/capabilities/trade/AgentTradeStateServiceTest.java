package server.agents.capabilities.trade;

import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTradeStateServiceTest {
    @Test
    void initializesSequenceLikeLegacyTradeState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentPendingTradeStateRuntime.markInviteAnnounced(entry);

        AgentTradeStateService.initializeSequence(entry, "equips", 123, false);

        assertEquals("equips", AgentPendingTradeStateRuntime.category(entry));
        assertEquals(123, AgentPendingTradeStateRuntime.recipientId(entry));
        assertFalse(AgentPendingTradeStateRuntime.singleBatch(entry));
        assertFalse(AgentPendingTradeStateRuntime.inviteAnnounced(entry));
    }

    @Test
    void initializesBatchWithFirstTradeWindowAndClearsProgress() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            items.add(new Item(2000000 + i, (short) 1, (short) 1));
        }
        AgentPendingTradeStateRuntime.incrementItemIndex(entry);
        AgentPendingTradeStateRuntime.setTimerMs(entry, 500);
        AgentPendingTradeStateRuntime.markMesoAdded(entry);
        AgentPendingTradeStateRuntime.markAllItemsAdded(entry);
        AgentPendingTradeStateRuntime.markBotDone(entry);

        AgentTradeStateService.initializeBatch(entry, items, 1000);

        assertEquals(9, AgentPendingTradeStateRuntime.items(entry).size());
        assertNotSame(items, AgentPendingTradeStateRuntime.items(entry));
        assertEquals(1000, AgentPendingTradeStateRuntime.meso(entry));
        assertEquals(0, AgentPendingTradeStateRuntime.itemIndex(entry));
        assertEquals(0, AgentPendingTradeStateRuntime.timerMs(entry));
        assertFalse(AgentPendingTradeStateRuntime.mesoAdded(entry));
        assertFalse(AgentPendingTradeStateRuntime.allItemsAdded(entry));
        assertFalse(AgentPendingTradeStateRuntime.botDone(entry));
    }

    @Test
    void enterBetweenBatchesClearsBatchItemsAndSetsDelay() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentPendingTradeStateRuntime.setItems(entry, List.of(new Item(2000000, (short) 1, (short) 1)));
        AgentPendingTradeStateRuntime.markAllItemsAdded(entry);
        AgentPendingTradeStateRuntime.markBotDone(entry);

        AgentTradeStateService.enterBetweenBatches(entry, 1000);

        assertTrue(AgentPendingTradeStateRuntime.isBetweenBatches(entry));
        assertFalse(AgentPendingTradeStateRuntime.allItemsAdded(entry));
        assertFalse(AgentPendingTradeStateRuntime.botDone(entry));
        assertEquals(1000, AgentPendingTradeStateRuntime.timerMs(entry));
    }

    @Test
    void clearSequenceClearsAllPendingTradeState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentPendingTradeStateRuntime.setCategory(entry, "pots");
        AgentPendingTradeStateRuntime.setCategoryMessage(entry, "message");
        AgentPendingTradeStateRuntime.setItems(entry, List.of(new Item(2000000, (short) 1, (short) 1)));
        AgentPendingTradeStateRuntime.setRecipientId(entry, 44);
        AgentPendingTradeStateRuntime.setMeso(entry, 100);
        AgentPendingTradeStateRuntime.incrementItemIndex(entry);
        AgentPendingTradeStateRuntime.setTimerMs(entry, 500);
        AgentPendingTradeStateRuntime.markMesoAdded(entry);
        AgentPendingTradeStateRuntime.markAllItemsAdded(entry);
        AgentPendingTradeStateRuntime.markBotDone(entry);
        AgentPendingTradeStateRuntime.setSingleBatch(entry, true);
        AgentPendingTradeStateRuntime.markInviteAnnounced(entry);
        AgentPendingTradeStateRuntime.setShareBudget(entry, 10);
        AgentPendingTradeStateRuntime.addOwnerGivenItem(entry, new Item(1040000, (short) 1, (short) 1));

        AgentTradeStateService.clearSequence(entry);

        assertNull(AgentPendingTradeStateRuntime.category(entry));
        assertNull(AgentPendingTradeStateRuntime.categoryMessage(entry));
        assertTrue(AgentPendingTradeStateRuntime.isBetweenBatches(entry));
        assertEquals(0, AgentPendingTradeStateRuntime.recipientId(entry));
        assertEquals(0, AgentPendingTradeStateRuntime.meso(entry));
        assertEquals(0, AgentPendingTradeStateRuntime.itemIndex(entry));
        assertEquals(0, AgentPendingTradeStateRuntime.timerMs(entry));
        assertFalse(AgentPendingTradeStateRuntime.mesoAdded(entry));
        assertFalse(AgentPendingTradeStateRuntime.allItemsAdded(entry));
        assertFalse(AgentPendingTradeStateRuntime.botDone(entry));
        assertFalse(AgentPendingTradeStateRuntime.singleBatch(entry));
        assertFalse(AgentPendingTradeStateRuntime.inviteAnnounced(entry));
        assertEquals(0, AgentPendingTradeStateRuntime.shareBudget(entry));
        assertFalse(AgentPendingTradeStateRuntime.hasOwnerGivenItems(entry));
    }
}
