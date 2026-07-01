package server.agents.capabilities.trade;

import org.junit.jupiter.api.Test;
import server.Trade;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.bots.BotEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AgentTradeCategoryAnnouncementServiceTest {
    @Test
    void noMessageDoesNothing() {
        BotEntry entry = new BotEntry(null, null, null);
        Trade trade = mock(Trade.class);

        boolean handled = AgentTradeCategoryAnnouncementService.announceBeforeFirstItem(entry, trade, () -> 600);

        assertFalse(handled);
        verify(trade, never()).chat(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void nonFirstItemDoesNothing() {
        BotEntry entry = new BotEntry(null, null, null);
        Trade trade = mock(Trade.class);
        AgentTradeStateService.initializeBatch(entry, java.util.List.of(), 0);
        AgentBotPendingTradeStateRuntime.setCategoryMessage(entry, "reserved");
        AgentBotPendingTradeStateRuntime.incrementItemIndex(entry);

        boolean handled = AgentTradeCategoryAnnouncementService.announceBeforeFirstItem(entry, trade, () -> 600);

        assertFalse(handled);
        assertEquals("reserved", AgentBotPendingTradeStateRuntime.categoryMessage(entry));
        verify(trade, never()).chat(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void firstItemWithMessageChatsAndSetsDelay() {
        BotEntry entry = new BotEntry(null, null, null);
        Trade trade = mock(Trade.class);
        AgentTradeStateService.initializeBatch(entry, java.util.List.of(), 0);
        AgentBotPendingTradeStateRuntime.setCategoryMessage(entry, "reserved");

        boolean handled = AgentTradeCategoryAnnouncementService.announceBeforeFirstItem(entry, trade, () -> 650);

        assertTrue(handled);
        verify(trade).chat("reserved");
        assertNull(AgentBotPendingTradeStateRuntime.categoryMessage(entry));
        assertEquals(650, AgentBotPendingTradeStateRuntime.timerMs(entry));
    }
}
