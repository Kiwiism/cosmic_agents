package server.agents.integration;

import server.agents.integration.cosmic.CosmicExpeditionGateway;

/** Installed expedition integration boundary. */
public final class AgentExpeditionGatewayRuntime {
    private static final ExpeditionGateway GATEWAY = CosmicExpeditionGateway.INSTANCE;

    private AgentExpeditionGatewayRuntime() {
    }

    public static ExpeditionGateway expedition() {
        return GATEWAY;
    }
}
