package server.agents.runtime;

import client.Character;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentMovementProfileService;
import server.agents.integration.AgentOwnerMotionStateRuntime;

public final class AgentLiveTickContextRuntime {
    private AgentLiveTickContextRuntime() {
    }

    public static AgentLiveTickContextService.Context prepareLiveTickContext(
            AgentRuntimeEntry entry,
            Character agent,
            Character leader,
            AgentLiveTickContextService.FollowAnchorResolver followAnchorResolver,
            AgentLiveTickContextService.TargetSnapshotCapture targetSnapshotCapture) {
        return AgentLiveTickContextService.prepareLiveTickContext(
                entry,
                agent,
                leader,
                new AgentLiveTickContextService.Hooks(
                        AgentMovementProfileService::refreshMovementProfile,
                        followAnchorResolver,
                        targetSnapshotCapture,
                        AgentTickStateMaintenanceService::updateObservedLeaderMotion,
                        AgentOwnerMotionStateRuntime::rememberOwnerPosition,
                        AgentTickStateMaintenanceService::clearFarmAnchorOnMapChange,
                        AgentTickStateMaintenanceService::clearPatrolOnMapChange,
                        (runtimeEntry, agentPosition, targetSnapshot) ->
                                AgentLocalAttackMoveWindowService.clearFollowActionMoveWindowIfSettled(
                                        runtimeEntry,
                                        agentPosition,
                                        targetSnapshot,
                                        AgentMovementPhysicsConfig.configuredFollowDist(),
                                        AgentMovementPhysicsConfig.configuredStopDist(),
                                        AgentMovementPhysicsConfig.configuredFollowYCap())));
    }
}
