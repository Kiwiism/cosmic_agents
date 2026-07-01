package server.agents.capabilities.trade;

import client.inventory.Item;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

public final class AgentTradeBetweenBatchCallbackService {
    private AgentTradeBetweenBatchCallbackService() {
    }

    public static AgentTradeBetweenBatchService.BetweenBatchCallbacks betweenBatchCallbacks(
            IntUnaryOperator tickDown,
            Function<String, List<Item>> collectItems,
            Function<String, String> nextEquipsGroup,
            Function<String, String> nextAmmoGroup,
            Function<String, String> equipsGroupMessage,
            Consumer<List<Item>> openTradeBatch,
            Runnable resetTradeState) {
        return AgentTradeBetweenBatchService.BetweenBatchCallbacks.of(
                tickDown,
                collectItems,
                nextEquipsGroup,
                nextAmmoGroup,
                equipsGroupMessage,
                openTradeBatch,
                resetTradeState);
    }
}
