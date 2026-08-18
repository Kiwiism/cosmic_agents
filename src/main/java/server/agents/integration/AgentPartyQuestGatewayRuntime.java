package server.agents.integration;

import server.agents.integration.cosmic.CosmicAgentServerAdapter;

public final class AgentPartyQuestGatewayRuntime {
    private AgentPartyQuestGatewayRuntime() {
    }

    public static PartyQuestGateway partyQuest() {
        return CosmicAgentServerAdapter.INSTANCE.partyQuest();
    }
}
