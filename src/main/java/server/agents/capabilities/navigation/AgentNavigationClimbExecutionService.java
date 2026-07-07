package server.agents.capabilities.navigation;

import client.Character;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentRopeMovementService;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Rope;

/**
 * Agent-owned climb edge side effects used by navigation execution.
 */
public final class AgentNavigationClimbExecutionService {
    private AgentNavigationClimbExecutionService() {
    }

    public static void startClimbing(AgentRuntimeEntry entry, Character agent, Rope rope, int climbY) {
        AgentRopeMovementService.attachToRope(entry, agent, rope, climbY);
        AgentMovementBroadcastService.broadcastMovement(entry);
    }
}
