package server.agents.capabilities.trade;

import server.agents.runtime.AgentRuntimeEntry;

import java.util.function.IntUnaryOperator;

public final class AgentTradeQueuedRetryService {
    private AgentTradeQueuedRetryService() {
    }

    public static boolean tickQueuedRetry(AgentRuntimeEntry entry, IntUnaryOperator tickDown) {
        if (!AgentPendingTradeStateRuntime.isIdle(entry)
                || !AgentPendingTradeStateRuntime.hasQueuedRetry(entry)) {
            return false;
        }
        if (AgentPendingTradeStateRuntime.retryDelayMs(entry) > 0) {
            AgentPendingTradeStateRuntime.setRetryDelayMs(
                    entry,
                    tickDown.applyAsInt(AgentPendingTradeStateRuntime.retryDelayMs(entry)));
            return true;
        }
        Runnable retry = AgentPendingTradeStateRuntime.takeRetry(entry);
        retry.run();
        return true;
    }
}
