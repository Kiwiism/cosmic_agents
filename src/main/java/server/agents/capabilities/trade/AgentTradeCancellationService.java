package server.agents.capabilities.trade;

import client.Character;
import server.Trade;
import server.agents.integration.AgentBotInventoryRuntime;
import server.agents.runtime.AgentRuntimeEntry;

public final class AgentTradeCancellationService {
    private AgentTradeCancellationService() {
    }

    public static void cancelSequence(AgentRuntimeEntry entry, Character agent, String message, Runnable resetTradeState) {
        AgentBotInventoryRuntime.replyNow(entry, message);
        if (agent.getTrade() != null) {
            Trade.cancelTrade(agent, Trade.TradeResult.NO_RESPONSE);
        }
        resetTradeState.run();
    }
}
