package server.agents.capabilities.trade;

import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.bots.BotEntry;

import java.util.function.IntUnaryOperator;

public final class AgentTradeQueuedRetryService {
    private AgentTradeQueuedRetryService() {
    }

    public static boolean tickQueuedRetry(BotEntry entry, IntUnaryOperator tickDown) {
        if (!AgentBotPendingTradeStateRuntime.isIdle(entry)
                || !AgentBotPendingTradeStateRuntime.hasQueuedRetry(entry)) {
            return false;
        }
        if (AgentBotPendingTradeStateRuntime.retryDelayMs(entry) > 0) {
            AgentBotPendingTradeStateRuntime.setRetryDelayMs(
                    entry,
                    tickDown.applyAsInt(AgentBotPendingTradeStateRuntime.retryDelayMs(entry)));
            return true;
        }
        Runnable retry = AgentBotPendingTradeStateRuntime.takeRetry(entry);
        retry.run();
        return true;
    }
}
