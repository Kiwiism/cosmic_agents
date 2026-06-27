package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed pending trade sequence state.
 */
public final class AgentBotPendingTradeStateRuntime {
    private AgentBotPendingTradeStateRuntime() {
    }

    public static boolean hasActiveSequence(BotEntry entry) {
        return entry.pendingTradeCategory() != null;
    }

    public static boolean isIdle(BotEntry entry) {
        return !hasActiveSequence(entry);
    }

    public static boolean hasQueuedRetry(BotEntry entry) {
        return entry.pendingBotTradeRetry() != null;
    }

    public static void queueRetry(BotEntry entry, Runnable retry, int delayMs) {
        if (hasQueuedRetry(entry)) {
            return;
        }
        entry.setPendingBotTradeRetry(retry);
        entry.setPendingBotTradeRetryMs(delayMs);
    }

    public static int retryDelayMs(BotEntry entry) {
        return entry.pendingBotTradeRetryMs();
    }

    public static void setRetryDelayMs(BotEntry entry, int delayMs) {
        entry.setPendingBotTradeRetryMs(delayMs);
    }

    public static Runnable takeRetry(BotEntry entry) {
        Runnable retry = entry.pendingBotTradeRetry();
        entry.setPendingBotTradeRetry(null);
        return retry;
    }
}
