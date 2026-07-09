package server.agents.integration;

import server.agents.integration.cosmic.CosmicAgentServerAdapter;

/**
 * Integration boundary for live packet gateway access.
 */
public final class AgentPacketGatewayRuntime {
    private AgentPacketGatewayRuntime() {
    }

    public static PacketGateway packets() {
        return CosmicAgentServerAdapter.INSTANCE.packets();
    }
}
