package server.agents.capabilities.trade;

import client.inventory.Item;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

public final class AgentTradeBetweenBatchService {
    private AgentTradeBetweenBatchService() {
    }

    public static boolean tickBetweenBatches(AgentRuntimeEntry entry, BetweenBatchCallbacks callbacks) {
        if (!AgentPendingTradeStateRuntime.isBetweenBatches(entry)) {
            return false;
        }
        if (AgentPendingTradeStateRuntime.singleBatch(entry)) {
            callbacks.resetTradeState();
            return true;
        }
        if (AgentPendingTradeStateRuntime.timerMs(entry) > 0) {
            AgentPendingTradeStateRuntime.tickTimerDown(entry, callbacks.tickDown());
            return true;
        }

        String category = AgentPendingTradeStateRuntime.category(entry);
        List<Item> next = callbacks.collectItems(category);
        if (next.isEmpty()) {
            String advanced = callbacks.nextEquipsGroup(category);
            if (advanced == null) {
                advanced = callbacks.nextAmmoGroup(category);
            }
            if (advanced != null) {
                AgentPendingTradeStateRuntime.setCategory(entry, advanced);
                AgentPendingTradeStateRuntime.setCategoryMessage(entry, callbacks.equipsGroupMessage(advanced));
                callbacks.openTradeBatch(callbacks.collectItems(advanced));
            } else {
                callbacks.resetTradeState();
            }
        } else {
            callbacks.openTradeBatch(next);
        }
        return true;
    }

    public interface BetweenBatchCallbacks {
        IntUnaryOperator tickDown();
        List<Item> collectItems(String category);
        String nextEquipsGroup(String category);
        String nextAmmoGroup(String category);
        String equipsGroupMessage(String category);
        void openTradeBatch(List<Item> items);
        void resetTradeState();

        static BetweenBatchCallbacks of(IntUnaryOperator tickDown,
                                        Function<String, List<Item>> collectItems,
                                        Function<String, String> nextEquipsGroup,
                                        Function<String, String> nextAmmoGroup,
                                        Function<String, String> equipsGroupMessage,
                                        java.util.function.Consumer<List<Item>> openTradeBatch,
                                        Runnable resetTradeState) {
            return new BetweenBatchCallbacks() {
                @Override public IntUnaryOperator tickDown() { return tickDown; }
                @Override public List<Item> collectItems(String category) { return collectItems.apply(category); }
                @Override public String nextEquipsGroup(String category) { return nextEquipsGroup.apply(category); }
                @Override public String nextAmmoGroup(String category) { return nextAmmoGroup.apply(category); }
                @Override public String equipsGroupMessage(String category) { return equipsGroupMessage.apply(category); }
                @Override public void openTradeBatch(List<Item> items) { openTradeBatch.accept(items); }
                @Override public void resetTradeState() { resetTradeState.run(); }
            };
        }
    }
}
