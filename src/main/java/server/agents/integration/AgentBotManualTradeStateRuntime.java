package server.agents.integration;

import server.Trade;
import server.agents.capabilities.trade.AgentManualTradeState;
import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed manual trade invite state.
 */
public final class AgentBotManualTradeStateRuntime {
    private AgentBotManualTradeStateRuntime() {
    }

    public static Trade tradeRef(BotEntry entry) {
        AgentManualTradeState state = state(entry);
        return state == null ? null : state.tradeRef();
    }

    public static void beginTrade(BotEntry entry, Trade trade, int timeoutMs) {
        AgentManualTradeState state = state(entry);
        if (state != null) {
            state.begin(trade, timeoutMs);
        }
    }

    public static int timeoutMs(BotEntry entry) {
        AgentManualTradeState state = state(entry);
        return state == null ? 0 : state.timeoutMs();
    }

    public static void setTimeoutMs(BotEntry entry, int timeoutMs) {
        AgentManualTradeState state = state(entry);
        if (state != null) {
            state.setTimeoutMs(timeoutMs);
        }
    }

    public static int acceptDelayMs(BotEntry entry) {
        AgentManualTradeState state = state(entry);
        return state == null ? 0 : state.acceptDelayMs();
    }

    public static void ensureAcceptDelay(BotEntry entry, int delayMs) {
        AgentManualTradeState state = state(entry);
        if (state != null) {
            state.ensureAcceptDelay(delayMs);
        }
    }

    public static void setAcceptDelayMs(BotEntry entry, int delayMs) {
        AgentManualTradeState state = state(entry);
        if (state != null) {
            state.setAcceptDelayMs(delayMs);
        }
    }

    public static void clear(BotEntry entry) {
        AgentManualTradeState state = state(entry);
        if (state != null) {
            state.clear();
        }
    }

    private static AgentManualTradeState state(BotEntry entry) {
        return entry == null ? null : entry.manualTradeState();
    }
}
