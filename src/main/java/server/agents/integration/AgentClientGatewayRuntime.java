package server.agents.integration;

import server.agents.integration.cosmic.CosmicAgentServerAdapter;

/**
 * Integration boundary for live headless Agent client access.
 */
public final class AgentClientGatewayRuntime {
    private AgentClientGatewayRuntime() {
    }

    public static AgentClientGateway clients() {
        return CosmicAgentServerAdapter.INSTANCE.agentClients();
    }
}
