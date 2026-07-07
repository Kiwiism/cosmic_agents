package server.agents.capabilities.trade;

import client.Character;
import server.Trade;
import server.agents.integration.AgentBotManualTradeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/**
 * Agent-owned manual trade state bookkeeping while the enclosing trade tick is
 * still hosted by the temporary bot inventory shell.
 */
public final class AgentManualTradeService {
    public static final int DEFAULT_MANUAL_TRADE_TIMEOUT_MS = 60_000;
    private static final Set<Integer> GREETING_SENT = ConcurrentHashMap.newKeySet();

    private AgentManualTradeService() {
    }

    public static boolean beginOrTickTimeout(AgentRuntimeEntry entry,
                                             Character agent,
                                             Trade trade,
                                             IntUnaryOperator tickDown) {
        return beginOrTickTimeout(entry, agent, trade, DEFAULT_MANUAL_TRADE_TIMEOUT_MS, tickDown);
    }

    public static boolean beginOrTickTimeout(AgentRuntimeEntry entry,
                                             Character agent,
                                             Trade trade,
                                             int timeoutMs,
                                             IntUnaryOperator tickDown) {
        if (trade != AgentBotManualTradeStateRuntime.tradeRef(entry)) {
            clearGreeting(agent);
            AgentBotManualTradeStateRuntime.beginTrade(entry, trade, timeoutMs);
            return false;
        }

        if (AgentBotManualTradeStateRuntime.timeoutMs(entry) <= 0) {
            return false;
        }

        AgentBotManualTradeStateRuntime.setTimeoutMs(
                entry,
                tickDown.applyAsInt(AgentBotManualTradeStateRuntime.timeoutMs(entry)));
        if (AgentBotManualTradeStateRuntime.timeoutMs(entry) != 0) {
            return false;
        }

        Trade.cancelTrade(agent, Trade.TradeResult.NO_RESPONSE);
        clearState(entry, agent);
        return true;
    }

    public static void clearState(AgentRuntimeEntry entry, Character agent) {
        clearGreeting(agent);
        AgentBotManualTradeStateRuntime.clear(entry);
    }

    public static void clearGreeting(Character agent) {
        if (agent != null) {
            GREETING_SENT.remove(agent.getId());
        }
    }

    public static void sendGreetingOnce(Character agent, Trade trade, Supplier<String> greeting) {
        if (agent != null && trade != null && GREETING_SENT.add(agent.getId())) {
            trade.chat(greeting.get());
        }
    }

    public static Trade acceptInviteWhenReady(AgentRuntimeEntry entry,
                                              Character agent,
                                              Character inviter,
                                              Trade trade,
                                              int delayMs,
                                              IntUnaryOperator tickDown) {
        if (trade.getNumber() != 1) {
            return trade;
        }

        AgentBotManualTradeStateRuntime.ensureAcceptDelay(entry, delayMs);
        AgentBotManualTradeStateRuntime.setAcceptDelayMs(
                entry,
                tickDown.applyAsInt(AgentBotManualTradeStateRuntime.acceptDelayMs(entry)));
        if (AgentBotManualTradeStateRuntime.acceptDelayMs(entry) > 0) {
            return trade;
        }

        Trade.visitTrade(agent, inviter);
        Trade joined = agent.getTrade();
        if (joined == null || !joined.isFullTrade()) {
            return null;
        }
        return joined;
    }
}
