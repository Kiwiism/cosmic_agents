package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementStateResetService;

import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementPoseService;

import client.Character;
import server.agents.integration.AgentBotReplyRuntime;
import server.bots.BotEntry;
import server.maps.MapleMap;

public final class AgentRespawnRuntime {
    private AgentRespawnRuntime() {
    }

    public static void respawnNearLeader(BotEntry entry, Character agent, Character leader) {
        AgentDeathTickService.respawnNearLeader(
                entry,
                agent,
                leader,
                new AgentDeathTickService.RespawnHooks(
                        (respawnAgent, leaderMap, leaderPosition) ->
                                respawnAgent.forceChangeMap(leaderMap, leaderMap.findClosestPortal(leaderPosition)),
                        MapleMap::getPointBelow,
                        AgentMovementPoseService::teleportTo,
                        (respawnEntry, ignoredAgent) -> AgentMovementStateResetService.resetEntryStateAfterTeleport(respawnEntry),
                        (respawnEntry, ignoredAgent) -> AgentMovementBroadcastService.broadcastMovement(respawnEntry),
                        AgentBotReplyRuntime::sayMapNow));
    }
}
