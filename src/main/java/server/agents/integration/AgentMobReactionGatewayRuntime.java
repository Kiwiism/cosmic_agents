package server.agents.integration;

import server.agents.integration.cosmic.CosmicAgentServerAdapter;

public final class AgentMobReactionGatewayRuntime {
    private AgentMobReactionGatewayRuntime() {
    }

    public static MobReactionGateway mobReactions() {
        return CosmicAgentServerAdapter.INSTANCE.mobReactions();
    }
}
