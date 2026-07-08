package server.agents.capabilities.trade;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.inventory.AgentInventoryRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class AgentTradeClosedWindowServiceTest {
    @Test
    void botDoneSingleBatchResetsAndRefills() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AtomicBoolean reset = new AtomicBoolean(false);
        AtomicBoolean refill = new AtomicBoolean(false);
        AgentTradeStateService.initializeSequence(entry, "scrolls", 1, true);
        AgentPendingTradeStateRuntime.markBotDone(entry);

        boolean handled = AgentTradeClosedWindowService.handleClosedTrade(
                entry,
                () -> 1_000,
                () -> reset.set(true),
                () -> refill.set(true));

        assertTrue(handled);
        assertTrue(reset.get());
        assertTrue(refill.get());
    }

    @Test
    void botDoneMultiBatchEntersBetweenBatches() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AtomicBoolean reset = new AtomicBoolean(false);
        AtomicBoolean refill = new AtomicBoolean(false);
        AgentTradeStateService.initializeSequence(entry, "scrolls", 1, false);
        AgentTradeStateService.initializeBatch(entry, java.util.List.of(), 0);
        AgentPendingTradeStateRuntime.markBotDone(entry);

        boolean handled = AgentTradeClosedWindowService.handleClosedTrade(
                entry,
                () -> 1_050,
                () -> reset.set(true),
                () -> refill.set(true));

        assertTrue(handled);
        assertNull(AgentPendingTradeStateRuntime.items(entry));
        assertEquals(1_050, AgentPendingTradeStateRuntime.timerMs(entry));
        assertTrue(!reset.get());
        assertTrue(!refill.get());
    }

    @Test
    void allItemsAddedReplyCancelledResetsAndRefills() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AtomicBoolean reset = new AtomicBoolean(false);
        AtomicBoolean refill = new AtomicBoolean(false);
        AgentPendingTradeStateRuntime.markAllItemsAdded(entry);

        try (MockedStatic<AgentInventoryRuntime> replies = mockStatic(AgentInventoryRuntime.class)) {
            boolean handled = AgentTradeClosedWindowService.handleClosedTrade(
                    entry,
                    () -> 1_000,
                    () -> reset.set(true),
                    () -> refill.set(true));

            assertTrue(handled);
            assertTrue(reset.get());
            assertTrue(refill.get());
            replies.verify(() -> AgentInventoryRuntime.replyNow(
                    entry,
                    AgentDialogueCatalog.tradeCancelledReply()));
        }
    }

    @Test
    void declinedInviteRepliesAndResetsOnly() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AtomicBoolean reset = new AtomicBoolean(false);
        AtomicBoolean refill = new AtomicBoolean(false);

        try (MockedStatic<AgentInventoryRuntime> replies = mockStatic(AgentInventoryRuntime.class)) {
            boolean handled = AgentTradeClosedWindowService.handleClosedTrade(
                    entry,
                    () -> 1_000,
                    () -> reset.set(true),
                    () -> refill.set(true));

            assertTrue(handled);
            assertTrue(reset.get());
            assertTrue(!refill.get());
            replies.verify(() -> AgentInventoryRuntime.replyNow(
                    entry,
                    AgentDialogueCatalog.tradeDeclinedReply()));
        }
    }
}
