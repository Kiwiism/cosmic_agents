package server.agents.capabilities.trade;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.integration.AgentBotInventoryRuntime;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.bots.BotEntry;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class AgentTradeClosedWindowServiceTest {
    @Test
    void botDoneSingleBatchResetsAndRefills() {
        BotEntry entry = new BotEntry(null, null, null);
        AtomicBoolean reset = new AtomicBoolean(false);
        AtomicBoolean refill = new AtomicBoolean(false);
        AgentTradeStateService.initializeSequence(entry, "scrolls", 1, true);
        AgentBotPendingTradeStateRuntime.markBotDone(entry);

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
        BotEntry entry = new BotEntry(null, null, null);
        AtomicBoolean reset = new AtomicBoolean(false);
        AtomicBoolean refill = new AtomicBoolean(false);
        AgentTradeStateService.initializeSequence(entry, "scrolls", 1, false);
        AgentTradeStateService.initializeBatch(entry, java.util.List.of(), 0);
        AgentBotPendingTradeStateRuntime.markBotDone(entry);

        boolean handled = AgentTradeClosedWindowService.handleClosedTrade(
                entry,
                () -> 1_050,
                () -> reset.set(true),
                () -> refill.set(true));

        assertTrue(handled);
        assertNull(AgentBotPendingTradeStateRuntime.items(entry));
        assertEquals(1_050, AgentBotPendingTradeStateRuntime.timerMs(entry));
        assertTrue(!reset.get());
        assertTrue(!refill.get());
    }

    @Test
    void allItemsAddedReplyCancelledResetsAndRefills() {
        BotEntry entry = new BotEntry(null, null, null);
        AtomicBoolean reset = new AtomicBoolean(false);
        AtomicBoolean refill = new AtomicBoolean(false);
        AgentBotPendingTradeStateRuntime.markAllItemsAdded(entry);

        try (MockedStatic<AgentBotInventoryRuntime> replies = mockStatic(AgentBotInventoryRuntime.class)) {
            boolean handled = AgentTradeClosedWindowService.handleClosedTrade(
                    entry,
                    () -> 1_000,
                    () -> reset.set(true),
                    () -> refill.set(true));

            assertTrue(handled);
            assertTrue(reset.get());
            assertTrue(refill.get());
            replies.verify(() -> AgentBotInventoryRuntime.replyNow(
                    entry,
                    AgentDialogueCatalog.tradeCancelledReply()));
        }
    }

    @Test
    void declinedInviteRepliesAndResetsOnly() {
        BotEntry entry = new BotEntry(null, null, null);
        AtomicBoolean reset = new AtomicBoolean(false);
        AtomicBoolean refill = new AtomicBoolean(false);

        try (MockedStatic<AgentBotInventoryRuntime> replies = mockStatic(AgentBotInventoryRuntime.class)) {
            boolean handled = AgentTradeClosedWindowService.handleClosedTrade(
                    entry,
                    () -> 1_000,
                    () -> reset.set(true),
                    () -> refill.set(true));

            assertTrue(handled);
            assertTrue(reset.get());
            assertTrue(!refill.get());
            replies.verify(() -> AgentBotInventoryRuntime.replyNow(
                    entry,
                    AgentDialogueCatalog.tradeDeclinedReply()));
        }
    }
}
