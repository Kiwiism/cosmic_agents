package server.agents.capabilities.trade;

import client.Character;
import server.agents.capabilities.inventory.AgentInventoryRuntime;
import server.agents.integration.AgentTradeGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;

public final class AgentTradeCancellationService {
    private AgentTradeCancellationService() {
    }

    public static void cancelSequence(AgentRuntimeEntry entry, Character agent, String message, Runnable resetTradeState) {
        AgentInventoryRuntime.replyNow(entry, message);
        if (agent.getTrade() != null) {
            AgentTradeGatewayRuntime.trade().cancelNoResponse(agent);
        }
        resetTradeState.run();
    }
}
