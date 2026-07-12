package server.agents.integration;

import server.agents.integration.cosmic.CosmicAgentServerAdapter;

public final class AgentPrimitiveCapabilityGatewayRuntime {
    private AgentPrimitiveCapabilityGatewayRuntime() {
    }

    public static PrimitiveCapabilityGateway gateway() {
        return CosmicAgentServerAdapter.INSTANCE.primitiveCapabilities();
    }
}
