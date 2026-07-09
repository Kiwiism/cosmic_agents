package server.agents.integration;

import server.agents.integration.cosmic.CosmicAgentServerAdapter;

public final class AgentCharacterGatewayRuntime {
    private AgentCharacterGatewayRuntime() {
    }

    public static CharacterGateway characters() {
        return CosmicAgentServerAdapter.INSTANCE.characters();
    }
}
