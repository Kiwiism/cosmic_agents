package server.agents.capabilities.trade;

import client.Character;
import server.agents.integration.AgentTradeGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
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
                                             AgentTradeWindow trade,
                                             IntUnaryOperator tickDown) {
        return beginOrTickTimeout(entry, agent, trade, DEFAULT_MANUAL_TRADE_TIMEOUT_MS, tickDown);
    }

    public static boolean beginOrTickTimeout(AgentRuntimeEntry entry,
                                             Character agent,
                                             AgentTradeWindow trade,
                                             int timeoutMs,
                                             IntUnaryOperator tickDown) {
        if (trade.identity() != AgentManualTradeStateRuntime.tradeRef(entry)) {
            clearGreeting(agent);
            AgentManualTradeStateRuntime.beginTrade(entry, trade.identity(), timeoutMs);
            return false;
        }

        if (AgentManualTradeStateRuntime.timeoutMs(entry) <= 0) {
            return false;
        }

        AgentManualTradeStateRuntime.setTimeoutMs(
                entry,
                tickDown.applyAsInt(AgentManualTradeStateRuntime.timeoutMs(entry)));
        if (AgentManualTradeStateRuntime.timeoutMs(entry) != 0) {
            return false;
        }

        AgentTradeGatewayRuntime.trade().cancelNoResponse(agent);
        clearState(entry, agent);
        return true;
    }

    public static void clearState(AgentRuntimeEntry entry, Character agent) {
        clearGreeting(agent);
        AgentManualTradeStateRuntime.clear(entry);
    }

    public static void clearGreeting(Character agent) {
        if (agent != null) {
            GREETING_SENT.remove(agent.getId());
        }
    }

    public static void sendGreetingOnce(Character agent, AgentTradeWindow trade, Supplier<String> greeting) {
        if (agent != null && trade != null && GREETING_SENT.add(agent.getId())) {
            trade.chat(greeting.get());
        }
    }

    public static AgentTradeWindow acceptInviteWhenReady(AgentRuntimeEntry entry,
                                                         Character agent,
                                                         Character inviter,
                                                         AgentTradeWindow trade,
                                                         int delayMs,
                                                         IntUnaryOperator tickDown,
                                                         Function<Character, AgentTradeWindow> currentTradeWindow) {
        if (trade.number() != 1) {
            return trade;
        }

        AgentManualTradeStateRuntime.ensureAcceptDelay(entry, delayMs);
        AgentManualTradeStateRuntime.setAcceptDelayMs(
                entry,
                tickDown.applyAsInt(AgentManualTradeStateRuntime.acceptDelayMs(entry)));
        if (AgentManualTradeStateRuntime.acceptDelayMs(entry) > 0) {
            return trade;
        }

        AgentTradeGatewayRuntime.trade().visitTrade(agent, inviter);
        AgentTradeWindow joined = currentTradeWindow.apply(agent);
        if (joined == null || !joined.isFullTrade()) {
            return null;
        }
        return joined;
    }
}
