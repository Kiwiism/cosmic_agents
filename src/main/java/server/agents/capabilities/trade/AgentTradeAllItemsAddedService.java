package server.agents.capabilities.trade;

import server.Trade;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.bots.BotEntry;

import java.util.List;
import java.util.function.Supplier;

public final class AgentTradeAllItemsAddedService {
    private AgentTradeAllItemsAddedService() {
    }

    public static boolean markCompleteIfNoMoreItems(BotEntry entry,
                                                    Trade trade,
                                                    Supplier<String> allDoneReply) {
        List<?> items = AgentBotPendingTradeStateRuntime.items(entry);
        int idx = AgentBotPendingTradeStateRuntime.itemIndex(entry);
        if (idx < items.size()) {
            return false;
        }

        AgentBotPendingTradeStateRuntime.markAllItemsAdded(entry);
        AgentBotPendingTradeStateRuntime.clearTimer(entry);
        trade.chat(allDoneReply.get());
        return true;
    }
}
