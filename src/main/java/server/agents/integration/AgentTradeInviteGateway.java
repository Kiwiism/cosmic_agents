package server.agents.integration;

import client.Character;

/**
 * Integration boundary for server-side trade creation and invitation.
 */
@AgentGatewayAffinity(
        value = AgentGatewayThreadAffinity.SHARD_SAFE_DIRECT,
        rationale = "Trade invitation delegates to Cosmic's synchronized trade service.")
public final class AgentTradeInviteGateway {
    private AgentTradeInviteGateway() {
    }

    public static void startAndInvite(Character agent, Character leader) {
        AgentTradeGatewayRuntime.trade().startTrade(agent);
        AgentTradeGatewayRuntime.trade().inviteTrade(agent, leader);
    }
}
