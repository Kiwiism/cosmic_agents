package server.agents.capabilities.trade;

import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.Trade;
import server.agents.integration.AgentPendingTradeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AgentTradeAllItemsAddedServiceTest {
    @Test
    void returnsFalseWhenItemsRemain() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Trade trade = mock(Trade.class);
        AgentTradeStateService.initializeBatch(entry, List.of(item()), 0);

        boolean handled = AgentTradeAllItemsAddedService.markCompleteIfNoMoreItems(entry, trade, () -> "done");

        assertFalse(handled);
        assertFalse(AgentPendingTradeStateRuntime.allItemsAdded(entry));
        verify(trade, never()).chat("done");
    }

    @Test
    void marksAllItemsAddedClearsTimerAndChatsWhenNoItemsRemain() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Trade trade = mock(Trade.class);
        AgentTradeStateService.initializeBatch(entry, List.of(item()), 0);
        AgentPendingTradeStateRuntime.incrementItemIndex(entry);
        AgentPendingTradeStateRuntime.setTimerMs(entry, 500);

        boolean handled = AgentTradeAllItemsAddedService.markCompleteIfNoMoreItems(entry, trade, () -> "done");

        assertTrue(handled);
        assertTrue(AgentPendingTradeStateRuntime.allItemsAdded(entry));
        assertEquals(0, AgentPendingTradeStateRuntime.timerMs(entry));
        verify(trade).chat("done");
    }

    private static Item item() {
        return new Item(2000000, (short) 1, (short) 1);
    }
}
