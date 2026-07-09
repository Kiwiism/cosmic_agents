package server.agents.capabilities.trade;

import server.agents.runtime.AgentRuntimeEntry;

import java.util.function.Consumer;
import java.util.function.IntSupplier;

public final class AgentTradeCategoryAnnouncementService {
    private AgentTradeCategoryAnnouncementService() {
    }

    public static boolean announceBeforeFirstItem(AgentRuntimeEntry entry,
                                                  Consumer<String> tradeChat,
                                                  IntSupplier delayMs) {
        if (AgentPendingTradeStateRuntime.itemIndex(entry) != 0
                || AgentPendingTradeStateRuntime.categoryMessage(entry) == null) {
            return false;
        }

        tradeChat.accept(AgentPendingTradeStateRuntime.takeCategoryMessage(entry));
        AgentPendingTradeStateRuntime.setTimerMs(entry, delayMs.getAsInt());
        return true;
    }
}
