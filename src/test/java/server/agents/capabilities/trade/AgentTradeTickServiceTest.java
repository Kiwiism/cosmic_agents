package server.agents.capabilities.trade;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTradeTickServiceTest {
    @Test
    void queuedRetryRunsBeforeTradeLookup() {
        AgentRuntimeEntry entry = entry();
        List<String> events = new ArrayList<>();
        AgentPendingTradeStateRuntime.queueRetry(entry, () -> events.add("retry"), 0);

        AgentTradeTickService.tickTrade(entry, mock(Character.class), callbacks(events, null));

        assertEquals(List.of("retry"), events);
    }

    @Test
    void idleStateDoesNothing() {
        List<String> events = new ArrayList<>();

        AgentTradeTickService.tickTrade(entry(), mock(Character.class), callbacks(events, null));

        assertEquals(List.of(), events);
    }

    @Test
    void betweenBatchRunsBeforeClosedWindowHandling() {
        AgentRuntimeEntry entry = activeEntry();
        List<String> events = new ArrayList<>();
        TraceCallbacks callbacks = callbacks(events, null);
        callbacks.betweenBatch = true;

        AgentTradeTickService.tickTrade(entry, mock(Character.class), callbacks);

        assertEquals(List.of("trade", "between"), events);
    }

    @Test
    void nullTradeHandlesClosedWindow() {
        AgentRuntimeEntry entry = activeEntry();
        List<String> events = new ArrayList<>();

        AgentTradeTickService.tickTrade(entry, mock(Character.class), callbacks(events, null));

        assertEquals(List.of("trade", "between", "closed"), events);
    }

    @Test
    void nonFullTradeWaitsForAccept() {
        AgentRuntimeEntry entry = activeEntry();
        AgentTradeWindow trade = mock(AgentTradeWindow.class);
        when(trade.isFullTrade()).thenReturn(false);
        List<String> events = new ArrayList<>();

        AgentTradeTickService.tickTrade(entry, mock(Character.class), callbacks(events, trade));

        assertEquals(List.of("trade", "between", "accept"), events);
    }

    @Test
    void addingItemsRunsBeforeConfirmation() {
        AgentRuntimeEntry entry = activeEntry();
        AgentTradeWindow trade = mock(AgentTradeWindow.class);
        when(trade.isFullTrade()).thenReturn(true);
        List<String> events = new ArrayList<>();
        TraceCallbacks callbacks = callbacks(events, trade);
        callbacks.adding = true;

        AgentTradeTickService.tickTrade(entry, mock(Character.class), callbacks);

        assertEquals(List.of("trade", "between", "adding"), events);
    }

    @Test
    void confirmationRunsWhenItemsAreAddedAndBotIsNotDone() {
        AgentRuntimeEntry entry = activeEntry();
        AgentTradeWindow trade = mock(AgentTradeWindow.class);
        when(trade.isFullTrade()).thenReturn(true);
        List<String> events = new ArrayList<>();

        AgentTradeTickService.tickTrade(entry, mock(Character.class), callbacks(events, trade));

        assertEquals(List.of("trade", "between", "adding", "confirm"), events);
    }

    @Test
    void botDoneWaitsForClosedWindowAfterItemsAreAdded() {
        AgentRuntimeEntry entry = activeEntry();
        AgentTradeWindow trade = mock(AgentTradeWindow.class);
        when(trade.isFullTrade()).thenReturn(true);
        AgentPendingTradeStateRuntime.markBotDone(entry);
        List<String> events = new ArrayList<>();

        AgentTradeTickService.tickTrade(entry, mock(Character.class), callbacks(events, trade));

        assertEquals(List.of("trade", "between", "adding"), events);
    }

    private static AgentRuntimeEntry entry() {
        return new AgentRuntimeEntry(mock(Character.class), null, null);
    }

    private static AgentRuntimeEntry activeEntry() {
        AgentRuntimeEntry entry = entry();
        AgentPendingTradeStateRuntime.setCategory(entry, "scrolls");
        return entry;
    }

    private static TraceCallbacks callbacks(List<String> events, AgentTradeWindow trade) {
        return new TraceCallbacks(events, trade);
    }

    private static final class TraceCallbacks implements AgentTradeTickService.TradeTickCallbacks {
        private final List<String> events;
        private final AgentTradeWindow trade;
        boolean betweenBatch;
        boolean adding;

        private TraceCallbacks(List<String> events, AgentTradeWindow trade) {
            this.events = events;
            this.trade = trade;
        }

        @Override
        public IntUnaryOperator tickDown() {
            return remaining -> remaining - 100;
        }

        @Override
        public AgentTradeWindow currentTrade() {
            events.add("trade");
            return trade;
        }

        @Override
        public boolean tickBetweenBatches() {
            events.add("between");
            return betweenBatch;
        }

        @Override
        public void handleClosedTrade() {
            events.add("closed");
        }

        @Override
        public void tickWaitingForAccept(AgentTradeWindow trade) {
            events.add("accept");
        }

        @Override
        public boolean tickAddingItems(AgentTradeWindow trade) {
            events.add("adding");
            return adding;
        }

        @Override
        public void tickWaitingForConfirmation(AgentTradeWindow trade) {
            events.add("confirm");
        }
    }
}
