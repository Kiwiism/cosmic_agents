package server.agents.integration;

import server.agents.integration.cosmic.CosmicAgentServerAdapter;

public final class AgentMapGatewayRuntime {
    private AgentMapGatewayRuntime() {
    }

    public static MapGateway map() {
        return CosmicAgentServerAdapter.INSTANCE.maps();
    }
}
