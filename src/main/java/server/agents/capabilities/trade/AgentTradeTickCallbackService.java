package server.agents.capabilities.trade;

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
            Supplier<AgentTradeWindow> currentTrade,
            BooleanSupplier tickBetweenBatches,
            Runnable handleClosedTrade,
            Consumer<AgentTradeWindow> tickWaitingForAccept,
            Predicate<AgentTradeWindow> tickAddingItems,
            Consumer<AgentTradeWindow> tickWaitingForConfirmation) {
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
