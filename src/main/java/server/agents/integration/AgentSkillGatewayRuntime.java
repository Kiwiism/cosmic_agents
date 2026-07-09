package server.agents.integration;

import server.agents.integration.cosmic.CosmicAgentServerAdapter;

/**
 * Integration boundary for live skill metadata access.
 */
public final class AgentSkillGatewayRuntime {
    private AgentSkillGatewayRuntime() {
    }

    public static SkillGateway skills() {
        return CosmicAgentServerAdapter.INSTANCE.skills();
    }
}
