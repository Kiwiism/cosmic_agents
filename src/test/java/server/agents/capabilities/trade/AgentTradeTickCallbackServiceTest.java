package server.agents.capabilities.trade;

import org.junit.jupiter.api.Test;
import server.Trade;
import server.agents.capabilities.trade.AgentTradeTickService.TradeTickCallbacks;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentTradeTickCallbackServiceTest {
    @Test
    void buildsTradeTickCallbacksFromLegacyOperations() {
        Trade trade = mock(Trade.class);
        AtomicBoolean between = new AtomicBoolean();
        AtomicBoolean closed = new AtomicBoolean();
        AtomicReference<Trade> acceptTrade = new AtomicReference<>();
        AtomicReference<Trade> addTrade = new AtomicReference<>();
        AtomicReference<Trade> confirmTrade = new AtomicReference<>();

        TradeTickCallbacks callbacks = AgentTradeTickCallbackService.tradeTickCallbacks(
                value -> value - 1,
                () -> trade,
                () -> {
                    between.set(true);
                    return true;
                },
                () -> closed.set(true),
                acceptTrade::set,
                currentTrade -> {
                    addTrade.set(currentTrade);
                    return true;
                },
                confirmTrade::set);

        assertEquals(4, callbacks.tickDown().applyAsInt(5));
        assertSame(trade, callbacks.currentTrade());
        assertTrue(callbacks.tickBetweenBatches());
        callbacks.handleClosedTrade();
        callbacks.tickWaitingForAccept(trade);
        assertTrue(callbacks.tickAddingItems(trade));
        callbacks.tickWaitingForConfirmation(trade);

        assertTrue(between.get());
        assertTrue(closed.get());
        assertSame(trade, acceptTrade.get());
        assertSame(trade, addTrade.get());
        assertSame(trade, confirmTrade.get());
    }
}
