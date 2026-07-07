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
                        runtimeEntry -> AgentMovementProfileService.refreshMovementProfile(asBotEntry(runtimeEntry)),
                        (runtimeEntry, runtimeLeader) -> followAnchorResolver.resolve(asBotEntry(runtimeEntry), runtimeLeader),
                        runtimeEntry -> targetSnapshotCapture.capture(asBotEntry(runtimeEntry)),
                        AgentTickStateMaintenanceService::updateObservedLeaderMotion,
                        AgentBotOwnerMotionStateRuntime::rememberOwnerPosition,
                        AgentTickStateMaintenanceService::clearFarmAnchorOnMapChange,
                        AgentTickStateMaintenanceService::clearPatrolOnMapChange,
                        (runtimeEntry, agentPosition, targetSnapshot) ->
                                AgentLocalAttackMoveWindowRuntime.clearFollowActionMoveWindowIfSettled(
                                        asBotEntry(runtimeEntry),
                                        agentPosition,
                                        targetSnapshot)));
    }

    private static BotEntry asBotEntry(AgentRuntimeEntry entry) {
        return (BotEntry) entry;
    }
}
