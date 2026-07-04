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
        return entry.manualTradeState().tradeRef();
    }

    public static void beginTrade(BotEntry entry, Trade trade, int timeoutMs) {
        entry.manualTradeState().begin(trade, timeoutMs);
    }

    public static int timeoutMs(BotEntry entry) {
        return entry.manualTradeState().timeoutMs();
    }

    public static void setTimeoutMs(BotEntry entry, int timeoutMs) {
        entry.manualTradeState().setTimeoutMs(timeoutMs);
    }

    public static int acceptDelayMs(BotEntry entry) {
        return entry.manualTradeState().acceptDelayMs();
    }

    public static void ensureAcceptDelay(BotEntry entry, int delayMs) {
        entry.manualTradeState().ensureAcceptDelay(delayMs);
    }

    public static void setAcceptDelayMs(BotEntry entry, int delayMs) {
        entry.manualTradeState().setAcceptDelayMs(delayMs);
    }

    public static void clear(BotEntry entry) {
        entry.manualTradeState().clear();
    }
}
