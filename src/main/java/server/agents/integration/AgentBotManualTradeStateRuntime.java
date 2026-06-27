package server.agents.integration;

import server.Trade;
import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed manual trade invite state.
 */
public final class AgentBotManualTradeStateRuntime {
    private AgentBotManualTradeStateRuntime() {
    }

    public static Trade tradeRef(BotEntry entry) {
        return entry.manualTradeRef();
    }

    public static void beginTrade(BotEntry entry, Trade trade, int timeoutMs) {
        entry.setManualTradeAcceptDelayMs(0);
        entry.setManualTradeRef(trade);
        entry.setManualTradeTimeoutMs(timeoutMs);
    }

    public static int timeoutMs(BotEntry entry) {
        return entry.manualTradeTimeoutMs();
    }

    public static void setTimeoutMs(BotEntry entry, int timeoutMs) {
        entry.setManualTradeTimeoutMs(timeoutMs);
    }

    public static int acceptDelayMs(BotEntry entry) {
        return entry.manualTradeAcceptDelayMs();
    }

    public static void ensureAcceptDelay(BotEntry entry, int delayMs) {
        if (entry.manualTradeAcceptDelayMs() == 0) {
            entry.setManualTradeAcceptDelayMs(delayMs);
        }
    }

    public static void setAcceptDelayMs(BotEntry entry, int delayMs) {
        entry.setManualTradeAcceptDelayMs(delayMs);
    }

    public static void clear(BotEntry entry) {
        entry.setManualTradeAcceptDelayMs(0);
        entry.setManualTradeRef(null);
        entry.setManualTradeTimeoutMs(0);
    }
}
