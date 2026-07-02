package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotManagerReplyRuntime;
import server.bots.BotEntry;
import server.bots.BotMovementManager;
import server.bots.BotPhysicsEngine;
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
                        BotPhysicsEngine::teleportTo,
                        (respawnEntry, ignoredAgent) -> BotMovementManager.resetEntryStateAfterTeleport(respawnEntry),
                        (respawnEntry, ignoredAgent) -> BotMovementManager.broadcastMovement(respawnEntry),
                        AgentBotManagerReplyRuntime::sayMapNow));
    }
}
