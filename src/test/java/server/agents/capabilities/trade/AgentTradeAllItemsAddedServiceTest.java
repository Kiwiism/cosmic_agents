package server.agents.capabilities.trade;

import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTradeAllItemsAddedServiceTest {
    @Test
    void returnsFalseWhenItemsRemain() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        List<String> chat = new ArrayList<>();
        AgentTradeStateService.initializeBatch(entry, List.of(item()), 0);

        boolean handled = AgentTradeAllItemsAddedService.markCompleteIfNoMoreItems(entry, chat::add, () -> "done");

        assertFalse(handled);
        assertFalse(AgentPendingTradeStateRuntime.allItemsAdded(entry));
        assertTrue(chat.isEmpty());
    }

    @Test
    void marksAllItemsAddedClearsTimerAndChatsWhenNoItemsRemain() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        List<String> chat = new ArrayList<>();
        AgentTradeStateService.initializeBatch(entry, List.of(item()), 0);
        AgentPendingTradeStateRuntime.incrementItemIndex(entry);
        AgentPendingTradeStateRuntime.setTimerMs(entry, 500);

        boolean handled = AgentTradeAllItemsAddedService.markCompleteIfNoMoreItems(entry, chat::add, () -> "done");

        assertTrue(handled);
        assertTrue(AgentPendingTradeStateRuntime.allItemsAdded(entry));
        assertEquals(0, AgentPendingTradeStateRuntime.timerMs(entry));
        assertEquals(List.of("done"), chat);
    }

    private static Item item() {
        return new Item(2000000, (short) 1, (short) 1);
    }
}
