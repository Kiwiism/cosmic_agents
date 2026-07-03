package server.agents.runtime;

import client.Character;
import server.agents.capabilities.movement.AgentMovementProfileService;
import server.agents.integration.AgentBotOwnerMotionStateRuntime;
import server.bots.BotEntry;

public final class AgentLiveTickContextRuntime {
    private AgentLiveTickContextRuntime() {
    }

    public static AgentLiveTickContextService.Context prepareLiveTickContext(
            BotEntry entry,
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
                        AgentBotOwnerMotionStateRuntime::rememberOwnerPosition,
                        AgentTickStateMaintenanceService::clearFarmAnchorOnMapChange,
                        AgentTickStateMaintenanceService::clearPatrolOnMapChange,
                        AgentLocalAttackMoveWindowRuntime::clearFollowActionMoveWindowIfSettled));
    }
}
