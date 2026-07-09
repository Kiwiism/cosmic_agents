package server.agents.integration;

import server.agents.integration.cosmic.CosmicAgentServerAdapter;

/**
 * Integration boundary for live quest sync access.
 */
public final class AgentQuestSyncGatewayRuntime {
    private AgentQuestSyncGatewayRuntime() {
    }

    public static AgentQuestSyncGateway quests() {
        return CosmicAgentServerAdapter.INSTANCE.questSync();
    }
}
