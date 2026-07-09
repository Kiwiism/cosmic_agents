package server.agents.capabilities.trade;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned adapter for AgentRuntimeEntry-backed manual trade invite state.
 */
public final class AgentManualTradeStateRuntime {
    private AgentManualTradeStateRuntime() {
    }

    public static Object tradeRef(AgentRuntimeEntry entry) {
        AgentManualTradeState state = state(entry);
        return state == null ? null : state.tradeRef();
    }

    public static void beginTrade(AgentRuntimeEntry entry, Object trade, int timeoutMs) {
        AgentManualTradeState state = state(entry);
        if (state != null) {
            state.begin(trade, timeoutMs);
        }
    }

    public static int timeoutMs(AgentRuntimeEntry entry) {
        AgentManualTradeState state = state(entry);
        return state == null ? 0 : state.timeoutMs();
    }

    public static void setTimeoutMs(AgentRuntimeEntry entry, int timeoutMs) {
        AgentManualTradeState state = state(entry);
        if (state != null) {
            state.setTimeoutMs(timeoutMs);
        }
    }

    public static int acceptDelayMs(AgentRuntimeEntry entry) {
        AgentManualTradeState state = state(entry);
        return state == null ? 0 : state.acceptDelayMs();
    }

    public static void ensureAcceptDelay(AgentRuntimeEntry entry, int delayMs) {
        AgentManualTradeState state = state(entry);
        if (state != null) {
            state.ensureAcceptDelay(delayMs);
        }
    }

    public static void setAcceptDelayMs(AgentRuntimeEntry entry, int delayMs) {
        AgentManualTradeState state = state(entry);
        if (state != null) {
            state.setAcceptDelayMs(delayMs);
        }
    }

    public static void clear(AgentRuntimeEntry entry) {
        AgentManualTradeState state = state(entry);
        if (state != null) {
            state.clear();
        }
    }

    private static AgentManualTradeState state(AgentRuntimeEntry entry) {
        return entry == null ? null : entry.manualTradeState();
    }
}
