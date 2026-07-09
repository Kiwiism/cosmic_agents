package server.agents.integration;

import server.agents.integration.cosmic.CosmicAgentServerAdapter;

/**
 * Integration boundary for live party gateway access.
 */
public final class AgentPartyGatewayRuntime {
    private AgentPartyGatewayRuntime() {
    }

    public static PartyGateway party() {
        return CosmicAgentServerAdapter.INSTANCE.party();
    }
}
