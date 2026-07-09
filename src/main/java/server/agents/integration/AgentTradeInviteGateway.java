package server.agents.integration;

import client.Character;

/**
 * Integration boundary for server-side trade creation and invitation.
 */
public final class AgentTradeInviteGateway {
    private AgentTradeInviteGateway() {
    }

    public static void startAndInvite(Character agent, Character leader) {
        AgentTradeGatewayRuntime.trade().startTrade(agent);
        AgentTradeGatewayRuntime.trade().inviteTrade(agent, leader);
    }
}
