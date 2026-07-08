package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.Trade;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTradeItemAddTickServiceTest {
    @Test
    void returnsFalseWhenAllItemsAlreadyAdded() {
        AgentRuntimeEntry entry = entry();
        AgentBotPendingTradeStateRuntime.markAllItemsAdded(entry);

        assertFalse(AgentTradeItemAddTickService.tickAddingItems(
                entry,
                mock(Character.class),
                mock(Trade.class),
                callbacks()));
    }

    @Test
    void ticksTimerBeforeOtherWork() {
        AgentRuntimeEntry entry = entry();
        AgentBotPendingTradeStateRuntime.setTimerMs(entry, 500);

        assertTrue(AgentTradeItemAddTickService.tickAddingItems(
                entry,
                mock(Character.class),
                mock(Trade.class),
                callbacks()));

        assertEquals(400, AgentBotPendingTradeStateRuntime.timerMs(entry));
    }

    @Test
    void cancelsWhenPendingMesoIsNoLongerAvailable() {
        AgentRuntimeEntry entry = entry();
        Character agent = mock(Character.class);
        Trade trade = mock(Trade.class);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AgentBotPendingTradeStateRuntime.setMeso(entry, 1_000);
        when(agent.getMeso()).thenReturn(500);

        assertTrue(AgentTradeItemAddTickService.tickAddingItems(
                entry,
                agent,
                trade,
                callbacks(cancelled)));

        assertTrue(cancelled.get());
    }

    @Test
    void marksCompleteWhenNoMoreItemsRemain() {
        AgentRuntimeEntry entry = entry();
        Trade trade = mock(Trade.class);
        AgentBotPendingTradeStateRuntime.setItems(entry, List.of());

        assertTrue(AgentTradeItemAddTickService.tickAddingItems(
                entry,
                mock(Character.class),
                trade,
                callbacks()));

        assertTrue(AgentBotPendingTradeStateRuntime.allItemsAdded(entry));
        verify(trade).chat("done");
    }

    @Test
    void announcesCategoryBeforeFirstItem() {
        AgentRuntimeEntry entry = entry();
        Trade trade = mock(Trade.class);
        AgentBotPendingTradeStateRuntime.setItems(entry, List.of(mock(Item.class)));
        AgentBotPendingTradeStateRuntime.setCategoryMessage(entry, "scrolls here");

        assertTrue(AgentTradeItemAddTickService.tickAddingItems(
                entry,
                mock(Character.class),
                trade,
                callbacks()));

        verify(trade).chat("scrolls here");
        assertEquals(600, AgentBotPendingTradeStateRuntime.timerMs(entry));
    }

    @Test
    void addsNextItemWhenReady() {
        AgentRuntimeEntry entry = entry();
        Character agent = mock(Character.class);
        Trade trade = mock(Trade.class);
        AgentBotPendingTradeStateRuntime.setItems(entry, List.of(mock(Item.class)));

        try (MockedStatic<AgentTradeItemAddService> addService = mockStatic(AgentTradeItemAddService.class)) {
            assertTrue(AgentTradeItemAddTickService.tickAddingItems(entry, agent, trade, callbacks()));

            addService.verify(() -> AgentTradeItemAddService.addNextItem(eq(entry), eq(agent), eq(trade), eq(500)));
        }
    }

    private static AgentRuntimeEntry entry() {
        return new AgentRuntimeEntry(mock(Character.class), null, null);
    }

    private static AgentTradeItemAddTickService.ItemAddTickCallbacks callbacks() {
        return callbacks(new AtomicBoolean(false));
    }

    private static AgentTradeItemAddTickService.ItemAddTickCallbacks callbacks(AtomicBoolean cancelled) {
        return AgentTradeItemAddTickService.ItemAddTickCallbacks.of(
                remaining -> remaining - 100,
                () -> cancelled.set(true),
                () -> 500,
                () -> "done",
                () -> 600,
                () -> 500);
    }
}
