package server.agents.integration;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Integration boundary for packet-visible combat stance refreshes.
 */
@AgentGatewayAffinity(
        value = AgentGatewayThreadAffinity.SHARD_SAFE_DIRECT,
        rationale = "Stance refresh broadcasts the owning Agent's current presentation state.")
public final class AgentCombatStanceGateway {
    private AgentCombatStanceGateway() {
    }

    public static void broadcastCurrentStance(AgentRuntimeEntry entry) {
        try {
            Character agent = AgentRuntimeIdentityRuntime.bot(entry);
            if (agent != null) {
                agent.broadcastStance();
            }
        } catch (Throwable ignored) {
        }
    }
}
