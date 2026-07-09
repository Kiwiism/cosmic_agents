package server.agents.capabilities.trade;

import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTradeCategoryAnnouncementServiceTest {
    @Test
    void noMessageDoesNothing() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        List<String> chat = new ArrayList<>();

        boolean handled = AgentTradeCategoryAnnouncementService.announceBeforeFirstItem(entry, chat::add, () -> 600);

        assertFalse(handled);
        assertTrue(chat.isEmpty());
    }

    @Test
    void nonFirstItemDoesNothing() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        List<String> chat = new ArrayList<>();
        AgentTradeStateService.initializeBatch(entry, java.util.List.of(), 0);
        AgentPendingTradeStateRuntime.setCategoryMessage(entry, "reserved");
        AgentPendingTradeStateRuntime.incrementItemIndex(entry);

        boolean handled = AgentTradeCategoryAnnouncementService.announceBeforeFirstItem(entry, chat::add, () -> 600);

        assertFalse(handled);
        assertEquals("reserved", AgentPendingTradeStateRuntime.categoryMessage(entry));
        assertTrue(chat.isEmpty());
    }

    @Test
    void firstItemWithMessageChatsAndSetsDelay() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        List<String> chat = new ArrayList<>();
        AgentTradeStateService.initializeBatch(entry, java.util.List.of(), 0);
        AgentPendingTradeStateRuntime.setCategoryMessage(entry, "reserved");

        boolean handled = AgentTradeCategoryAnnouncementService.announceBeforeFirstItem(entry, chat::add, () -> 650);

        assertTrue(handled);
        assertEquals(List.of("reserved"), chat);
        assertNull(AgentPendingTradeStateRuntime.categoryMessage(entry));
        assertEquals(650, AgentPendingTradeStateRuntime.timerMs(entry));
    }
}
