package server.agents.capabilities.trade;

import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
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
        AgentBotPendingTradeStateRuntime.markInviteAnnounced(entry);

        AgentTradeStateService.initializeSequence(entry, "equips", 123, false);

        assertEquals("equips", AgentBotPendingTradeStateRuntime.category(entry));
        assertEquals(123, AgentBotPendingTradeStateRuntime.recipientId(entry));
        assertFalse(AgentBotPendingTradeStateRuntime.singleBatch(entry));
        assertFalse(AgentBotPendingTradeStateRuntime.inviteAnnounced(entry));
    }

    @Test
    void initializesBatchWithFirstTradeWindowAndClearsProgress() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            items.add(new Item(2000000 + i, (short) 1, (short) 1));
        }
        AgentBotPendingTradeStateRuntime.incrementItemIndex(entry);
        AgentBotPendingTradeStateRuntime.setTimerMs(entry, 500);
        AgentBotPendingTradeStateRuntime.markMesoAdded(entry);
        AgentBotPendingTradeStateRuntime.markAllItemsAdded(entry);
        AgentBotPendingTradeStateRuntime.markBotDone(entry);

        AgentTradeStateService.initializeBatch(entry, items, 1000);

        assertEquals(9, AgentBotPendingTradeStateRuntime.items(entry).size());
        assertNotSame(items, AgentBotPendingTradeStateRuntime.items(entry));
        assertEquals(1000, AgentBotPendingTradeStateRuntime.meso(entry));
        assertEquals(0, AgentBotPendingTradeStateRuntime.itemIndex(entry));
        assertEquals(0, AgentBotPendingTradeStateRuntime.timerMs(entry));
        assertFalse(AgentBotPendingTradeStateRuntime.mesoAdded(entry));
        assertFalse(AgentBotPendingTradeStateRuntime.allItemsAdded(entry));
        assertFalse(AgentBotPendingTradeStateRuntime.botDone(entry));
    }

    @Test
    void enterBetweenBatchesClearsBatchItemsAndSetsDelay() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentBotPendingTradeStateRuntime.setItems(entry, List.of(new Item(2000000, (short) 1, (short) 1)));
        AgentBotPendingTradeStateRuntime.markAllItemsAdded(entry);
        AgentBotPendingTradeStateRuntime.markBotDone(entry);

        AgentTradeStateService.enterBetweenBatches(entry, 1000);

        assertTrue(AgentBotPendingTradeStateRuntime.isBetweenBatches(entry));
        assertFalse(AgentBotPendingTradeStateRuntime.allItemsAdded(entry));
        assertFalse(AgentBotPendingTradeStateRuntime.botDone(entry));
        assertEquals(1000, AgentBotPendingTradeStateRuntime.timerMs(entry));
    }

    @Test
    void clearSequenceClearsAllPendingTradeState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentBotPendingTradeStateRuntime.setCategory(entry, "pots");
        AgentBotPendingTradeStateRuntime.setCategoryMessage(entry, "message");
        AgentBotPendingTradeStateRuntime.setItems(entry, List.of(new Item(2000000, (short) 1, (short) 1)));
        AgentBotPendingTradeStateRuntime.setRecipientId(entry, 44);
        AgentBotPendingTradeStateRuntime.setMeso(entry, 100);
        AgentBotPendingTradeStateRuntime.incrementItemIndex(entry);
        AgentBotPendingTradeStateRuntime.setTimerMs(entry, 500);
        AgentBotPendingTradeStateRuntime.markMesoAdded(entry);
        AgentBotPendingTradeStateRuntime.markAllItemsAdded(entry);
        AgentBotPendingTradeStateRuntime.markBotDone(entry);
        AgentBotPendingTradeStateRuntime.setSingleBatch(entry, true);
        AgentBotPendingTradeStateRuntime.markInviteAnnounced(entry);
        AgentBotPendingTradeStateRuntime.setShareBudget(entry, 10);
        AgentBotPendingTradeStateRuntime.addOwnerGivenItem(entry, new Item(1040000, (short) 1, (short) 1));

        AgentTradeStateService.clearSequence(entry);

        assertNull(AgentBotPendingTradeStateRuntime.category(entry));
        assertNull(AgentBotPendingTradeStateRuntime.categoryMessage(entry));
        assertTrue(AgentBotPendingTradeStateRuntime.isBetweenBatches(entry));
        assertEquals(0, AgentBotPendingTradeStateRuntime.recipientId(entry));
        assertEquals(0, AgentBotPendingTradeStateRuntime.meso(entry));
        assertEquals(0, AgentBotPendingTradeStateRuntime.itemIndex(entry));
        assertEquals(0, AgentBotPendingTradeStateRuntime.timerMs(entry));
        assertFalse(AgentBotPendingTradeStateRuntime.mesoAdded(entry));
        assertFalse(AgentBotPendingTradeStateRuntime.allItemsAdded(entry));
        assertFalse(AgentBotPendingTradeStateRuntime.botDone(entry));
        assertFalse(AgentBotPendingTradeStateRuntime.singleBatch(entry));
        assertFalse(AgentBotPendingTradeStateRuntime.inviteAnnounced(entry));
        assertEquals(0, AgentBotPendingTradeStateRuntime.shareBudget(entry));
        assertFalse(AgentBotPendingTradeStateRuntime.hasOwnerGivenItems(entry));
    }
}
