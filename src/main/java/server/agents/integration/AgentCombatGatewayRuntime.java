package server.agents.integration;

import server.agents.integration.cosmic.CosmicAgentServerAdapter;

/**
 * Integration boundary for live combat gateway access.
 */
public final class AgentCombatGatewayRuntime {
    private AgentCombatGatewayRuntime() {
    }

    public static CombatGateway combat() {
        return CosmicAgentServerAdapter.INSTANCE.combat();
    }
}
