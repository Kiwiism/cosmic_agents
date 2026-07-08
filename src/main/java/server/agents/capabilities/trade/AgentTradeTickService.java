package server.agents.capabilities.trade;

import client.Character;
import server.Trade;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

public final class AgentTradeTickService {
    private AgentTradeTickService() {
    }

    public static void tickTrade(AgentRuntimeEntry entry, Character agent, TradeTickCallbacks callbacks) {
        if (AgentTradeQueuedRetryService.tickQueuedRetry(entry, callbacks.tickDown())) {
            return;
        }
        if (AgentPendingTradeStateRuntime.isIdle(entry)) {
            return;
        }

        Trade trade = callbacks.currentTrade();

        if (callbacks.tickBetweenBatches()) {
            return;
        }

        if (trade == null) {
            callbacks.handleClosedTrade();
            return;
        }

        if (!trade.isFullTrade()) {
            callbacks.tickWaitingForAccept(trade);
            return;
        }

        if (callbacks.tickAddingItems(trade)) {
            return;
        }

        if (!AgentPendingTradeStateRuntime.botDone(entry)) {
            callbacks.tickWaitingForConfirmation(trade);
        }
    }

    public interface TradeTickCallbacks {
        IntUnaryOperator tickDown();
        Trade currentTrade();
        boolean tickBetweenBatches();
        void handleClosedTrade();
        void tickWaitingForAccept(Trade trade);
        boolean tickAddingItems(Trade trade);
        void tickWaitingForConfirmation(Trade trade);

        static TradeTickCallbacks of(IntUnaryOperator tickDown,
                                     Supplier<Trade> currentTrade,
                                     java.util.function.BooleanSupplier tickBetweenBatches,
                                     Runnable handleClosedTrade,
                                     java.util.function.Consumer<Trade> tickWaitingForAccept,
                                     java.util.function.Predicate<Trade> tickAddingItems,
                                     java.util.function.Consumer<Trade> tickWaitingForConfirmation) {
            return new TradeTickCallbacks() {
                @Override public IntUnaryOperator tickDown() { return tickDown; }
                @Override public Trade currentTrade() { return currentTrade.get(); }
                @Override public boolean tickBetweenBatches() { return tickBetweenBatches.getAsBoolean(); }
                @Override public void handleClosedTrade() { handleClosedTrade.run(); }
                @Override public void tickWaitingForAccept(Trade trade) { tickWaitingForAccept.accept(trade); }
                @Override public boolean tickAddingItems(Trade trade) { return tickAddingItems.test(trade); }
                @Override public void tickWaitingForConfirmation(Trade trade) { tickWaitingForConfirmation.accept(trade); }
            };
        }
    }
}
