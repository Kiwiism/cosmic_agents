package server.agents.integration;

import client.Character;
import server.agents.integration.cosmic.CosmicAgentServerAdapter;

/**
 * Integration boundary for server-side trade creation and invitation.
 */
public final class AgentTradeInviteGateway {
    private AgentTradeInviteGateway() {
    }

    public static void startAndInvite(Character agent, Character leader) {
        CosmicAgentServerAdapter.INSTANCE.trade().startTrade(agent);
        CosmicAgentServerAdapter.INSTANCE.trade().inviteTrade(agent, leader);
    }
}
