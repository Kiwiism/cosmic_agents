package server.agents.capabilities.trade;

import server.Trade;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.function.IntSupplier;

public final class AgentTradeCategoryAnnouncementService {
    private AgentTradeCategoryAnnouncementService() {
    }

    public static boolean announceBeforeFirstItem(AgentRuntimeEntry entry, Trade trade, IntSupplier delayMs) {
        if (AgentBotPendingTradeStateRuntime.itemIndex(entry) != 0
                || AgentBotPendingTradeStateRuntime.categoryMessage(entry) == null) {
            return false;
        }

        trade.chat(AgentBotPendingTradeStateRuntime.takeCategoryMessage(entry));
        AgentBotPendingTradeStateRuntime.setTimerMs(entry, delayMs.getAsInt());
        return true;
    }
}
