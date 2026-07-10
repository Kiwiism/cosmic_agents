package server.agents.integration;

import server.agents.integration.cosmic.CosmicAgentServerAdapter;

public final class AgentPersistenceGatewayRuntime {
    private AgentPersistenceGatewayRuntime() {
    }

    public static AgentPersistenceGateway persistence() {
        return CosmicAgentServerAdapter.INSTANCE.persistence();
    }
}
