package server.agents.capabilities.trade;

import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class AgentTradeAllItemsAddedService {
    private AgentTradeAllItemsAddedService() {
    }

    public static boolean markCompleteIfNoMoreItems(AgentRuntimeEntry entry,
                                                    Consumer<String> tradeChat,
                                                    Supplier<String> allDoneReply) {
        List<?> items = AgentPendingTradeStateRuntime.items(entry);
        int idx = AgentPendingTradeStateRuntime.itemIndex(entry);
        if (idx < items.size()) {
            return false;
        }

        AgentPendingTradeStateRuntime.markAllItemsAdded(entry);
        AgentPendingTradeStateRuntime.clearTimer(entry);
        tradeChat.accept(allDoneReply.get());
        return true;
    }
}
