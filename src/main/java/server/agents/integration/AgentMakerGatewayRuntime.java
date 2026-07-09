package server.agents.integration;

import server.agents.integration.cosmic.CosmicAgentServerAdapter;

/**
 * Integration boundary for live Maker processor access.
 */
public final class AgentMakerGatewayRuntime {
    private AgentMakerGatewayRuntime() {
    }

    public static MakerGateway maker() {
        return CosmicAgentServerAdapter.INSTANCE.maker();
    }
}
