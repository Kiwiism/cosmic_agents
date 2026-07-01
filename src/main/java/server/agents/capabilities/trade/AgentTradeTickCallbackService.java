package server.agents.capabilities.trade;

import server.Trade;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class AgentTradeTickCallbackService {
    private AgentTradeTickCallbackService() {
    }

    public static AgentTradeTickService.TradeTickCallbacks tradeTickCallbacks(
            IntUnaryOperator tickDown,
            Supplier<Trade> currentTrade,
            BooleanSupplier tickBetweenBatches,
            Runnable handleClosedTrade,
            Consumer<Trade> tickWaitingForAccept,
            Predicate<Trade> tickAddingItems,
            Consumer<Trade> tickWaitingForConfirmation) {
        return AgentTradeTickService.TradeTickCallbacks.of(
                tickDown,
                currentTrade,
                tickBetweenBatches,
                handleClosedTrade,
                tickWaitingForAccept,
                tickAddingItems,
                tickWaitingForConfirmation);
    }
}
