package server.agents.integration;

import server.agents.integration.cosmic.CosmicAgentServerAdapter;

/**
 * Integration boundary for live shop gateway access.
 */
public final class AgentShopGatewayRuntime {
    private AgentShopGatewayRuntime() {
    }

    public static ShopGateway shop() {
        return CosmicAgentServerAdapter.INSTANCE.shop();
    }
}
