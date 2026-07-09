package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.capabilities.movement.AgentGroundingService;
import server.agents.capabilities.movement.AgentMovementPoseService;

import client.Character;
import server.agents.integration.AgentMapGatewayRuntime;

public final class AgentFollowMapSyncRuntime {
    private AgentFollowMapSyncRuntime() {
    }

    public static boolean syncFollowMap(AgentRuntimeEntry entry, Character agent, Character followAnchor) {
        return AgentFollowMapSyncService.syncFollowMap(
                entry,
                agent,
                followAnchor,
                new AgentFollowMapSyncService.FollowMapSyncHooks(
                        AgentGroundingService::findGroundPoint,
                        AgentMovementPoseService::idleOnGround,
                        AgentMapGatewayRuntime.map()::changeMap,
                        AgentMovementStateResetService::resetEntryState));
    }
}
