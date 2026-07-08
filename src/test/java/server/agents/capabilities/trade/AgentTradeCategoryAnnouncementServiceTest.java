package server.agents.capabilities.trade;

import org.junit.jupiter.api.Test;
import server.Trade;
import server.agents.integration.AgentPendingTradeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

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
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Trade trade = mock(Trade.class);

        boolean handled = AgentTradeCategoryAnnouncementService.announceBeforeFirstItem(entry, trade, () -> 600);

        assertFalse(handled);
        verify(trade, never()).chat(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void nonFirstItemDoesNothing() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Trade trade = mock(Trade.class);
        AgentTradeStateService.initializeBatch(entry, java.util.List.of(), 0);
        AgentPendingTradeStateRuntime.setCategoryMessage(entry, "reserved");
        AgentPendingTradeStateRuntime.incrementItemIndex(entry);

        boolean handled = AgentTradeCategoryAnnouncementService.announceBeforeFirstItem(entry, trade, () -> 600);

        assertFalse(handled);
        assertEquals("reserved", AgentPendingTradeStateRuntime.categoryMessage(entry));
        verify(trade, never()).chat(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void firstItemWithMessageChatsAndSetsDelay() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Trade trade = mock(Trade.class);
        AgentTradeStateService.initializeBatch(entry, java.util.List.of(), 0);
        AgentPendingTradeStateRuntime.setCategoryMessage(entry, "reserved");

        boolean handled = AgentTradeCategoryAnnouncementService.announceBeforeFirstItem(entry, trade, () -> 650);

        assertTrue(handled);
        verify(trade).chat("reserved");
        assertNull(AgentPendingTradeStateRuntime.categoryMessage(entry));
        assertEquals(650, AgentPendingTradeStateRuntime.timerMs(entry));
    }
}
