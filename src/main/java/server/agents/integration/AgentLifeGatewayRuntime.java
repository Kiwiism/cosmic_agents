package server.agents.integration;

import server.agents.integration.cosmic.CosmicAgentServerAdapter;

/**
 * Integration boundary for live life-factory access.
 */
public final class AgentLifeGatewayRuntime {
    private AgentLifeGatewayRuntime() {
    }

    public static LifeGateway life() {
        return CosmicAgentServerAdapter.INSTANCE.life();
    }
}
