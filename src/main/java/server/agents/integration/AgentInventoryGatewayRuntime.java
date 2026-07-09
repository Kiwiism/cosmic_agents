package server.agents.integration;

import server.agents.integration.cosmic.CosmicAgentServerAdapter;

/**
 * Integration boundary for live inventory metadata gateway access.
 */
public final class AgentInventoryGatewayRuntime {
    private AgentInventoryGatewayRuntime() {
    }

    public static InventoryGateway inventory() {
        return CosmicAgentServerAdapter.INSTANCE.inventory();
    }
}
