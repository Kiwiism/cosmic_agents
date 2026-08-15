package server.agents.integration;

import client.Character;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.runtime.AgentRuntimeEntry;

/** Read-only integration boundary for consumers that must distinguish graph preparation from inactivity. */
public final class AgentNavigationReadinessRuntime {
    private AgentNavigationReadinessRuntime() {
    }

    public static boolean warmupPending(AgentRuntimeEntry entry, int mapId) {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null || agent.getMap() == null || agent.getMapId() != mapId) {
            return false;
        }
        return AgentNavigationGraphService.isWarmupPending(
                agent.getMap(), AgentMovementStateRuntime.movementProfile(entry));
    }
}
