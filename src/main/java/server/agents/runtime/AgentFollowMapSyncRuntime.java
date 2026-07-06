package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.capabilities.movement.AgentGroundingService;
import server.agents.capabilities.movement.AgentMovementPoseService;

import client.Character;
import server.bots.BotEntry;

public final class AgentFollowMapSyncRuntime {
    private AgentFollowMapSyncRuntime() {
    }

    public static boolean syncFollowMap(BotEntry entry, Character agent, Character followAnchor) {
        return AgentFollowMapSyncService.syncFollowMap(
                entry,
                agent,
                followAnchor,
                new AgentFollowMapSyncService.FollowMapSyncHooks(
                        AgentGroundingService::findGroundPoint,
                        (hookEntry, hookAgent) -> AgentMovementPoseService.idleOnGround(asBotEntry(hookEntry), hookAgent),
                        Character::changeMap,
                        hookEntry -> AgentMovementStateResetService.resetEntryState(asBotEntry(hookEntry))));
    }

    private static BotEntry asBotEntry(AgentRuntimeEntry entry) {
        return (BotEntry) entry;
    }
}
