package server.agents.capabilities.follow;

import client.Character;
import server.agents.capabilities.movement.AgentGroundingService;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Coordinates map and movement operations required to keep a following Agent on
 * its anchor's map.
 */
public final class AgentFollowMapSyncCoordinator {
    private AgentFollowMapSyncCoordinator() {
    }

    public static boolean syncFollowMap(AgentRuntimeEntry entry,
                                        Character agent,
                                        Character followAnchor) {
        return syncFollowMap(
                entry,
                agent,
                followAnchor,
                new AgentFollowMapSyncService.FollowMapSyncHooks(
                        AgentGroundingService::findGroundPoint,
                        AgentMovementPoseService::idleOnGround,
                        AgentMapGatewayRuntime.map()::changeMap,
                        AgentMovementStateResetService::resetEntryState));
    }

    static boolean syncFollowMap(AgentRuntimeEntry entry,
                                 Character agent,
                                 Character followAnchor,
                                 AgentFollowMapSyncService.FollowMapSyncHooks hooks) {
        return AgentFollowMapSyncService.syncFollowMap(entry, agent, followAnchor, hooks);
    }
}
