package server.agents.integration;

import server.agents.integration.cosmic.CosmicAgentServerAdapter;

public final class AgentIdentityGatewayRuntime {
    private AgentIdentityGatewayRuntime() {
    }

    public static AgentIdentityGateway identities() {
        return CosmicAgentServerAdapter.INSTANCE.identities();
    }
}
