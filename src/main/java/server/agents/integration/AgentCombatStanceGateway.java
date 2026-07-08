package server.agents.integration;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Integration boundary for packet-visible combat stance refreshes.
 */
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
