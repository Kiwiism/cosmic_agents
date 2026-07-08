package server.agents.integration;

import client.Character;
import server.Trade;

/**
 * Integration boundary for server-side trade creation and invitation.
 */
public final class AgentTradeInviteGateway {
    private AgentTradeInviteGateway() {
    }

    public static void startAndInvite(Character agent, Character leader) {
        Trade.startTrade(agent);
        Trade.inviteTrade(agent, leader);
    }
}
