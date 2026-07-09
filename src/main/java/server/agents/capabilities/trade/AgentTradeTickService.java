package server.agents.capabilities.trade;

import client.Character;
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

        AgentTradeWindow trade = callbacks.currentTrade();

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
        AgentTradeWindow currentTrade();
        boolean tickBetweenBatches();
        void handleClosedTrade();
        void tickWaitingForAccept(AgentTradeWindow trade);
        boolean tickAddingItems(AgentTradeWindow trade);
        void tickWaitingForConfirmation(AgentTradeWindow trade);

        static TradeTickCallbacks of(IntUnaryOperator tickDown,
                                     Supplier<AgentTradeWindow> currentTrade,
                                     java.util.function.BooleanSupplier tickBetweenBatches,
                                     Runnable handleClosedTrade,
                                     java.util.function.Consumer<AgentTradeWindow> tickWaitingForAccept,
                                     java.util.function.Predicate<AgentTradeWindow> tickAddingItems,
                                     java.util.function.Consumer<AgentTradeWindow> tickWaitingForConfirmation) {
            return new TradeTickCallbacks() {
                @Override public IntUnaryOperator tickDown() { return tickDown; }
                @Override public AgentTradeWindow currentTrade() { return currentTrade.get(); }
                @Override public boolean tickBetweenBatches() { return tickBetweenBatches.getAsBoolean(); }
                @Override public void handleClosedTrade() { handleClosedTrade.run(); }
                @Override public void tickWaitingForAccept(AgentTradeWindow trade) { tickWaitingForAccept.accept(trade); }
                @Override public boolean tickAddingItems(AgentTradeWindow trade) { return tickAddingItems.test(trade); }
                @Override public void tickWaitingForConfirmation(AgentTradeWindow trade) { tickWaitingForConfirmation.accept(trade); }
            };
        }
    }
}
