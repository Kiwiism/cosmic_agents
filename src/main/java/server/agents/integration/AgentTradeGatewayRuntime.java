package server.agents.integration;

import server.agents.integration.cosmic.CosmicAgentServerAdapter;

/**
 * Integration boundary for live trade gateway access.
 */
public final class AgentTradeGatewayRuntime {
    private AgentTradeGatewayRuntime() {
    }

    public static TradeGateway trade() {
        return CosmicAgentServerAdapter.INSTANCE.trade();
    }
}
