package server.agents.capabilities.movement;

import client.Character;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Assembles movement capability operations required to ground an Agent after a
 * map change.
 */
public final class AgentMapGroundingCoordinator {
    private AgentMapGroundingCoordinator() {
    }

    public static boolean groundAfterMapChange(AgentRuntimeEntry entry, Character agent) {
        return AgentMapTransitionService.groundAfterMapChange(entry, agent, groundingHooks());
    }

    public static AgentMapTransitionService.GroundingHooks groundingHooks() {
        return new AgentMapTransitionService.GroundingHooks(
                AgentFootholdIndexService::buildFhIndex,
                AgentGroundingService::findGroundPoint,
                AgentMovementPoseService::teleportTo,
                AgentSpawnFallService::beginFall,
                AgentMovementStateResetService::resetEntryStateAfterTeleport,
                AgentNavigationGraphService::warmGraphAsync,
                AgentMovementBroadcastService::broadcastMovement);
    }
}
