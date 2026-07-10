package server.agents.runtime;

import server.agents.capabilities.combat.AgentDeathTickService;

import server.agents.capabilities.movement.AgentMovementStateResetService;

import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementPoseService;

import client.Character;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentReplyRuntime;

public final class AgentRespawnRuntime {
    private AgentRespawnRuntime() {
    }

    public static void respawnNearLeader(AgentRuntimeEntry entry, Character agent, Character leader) {
        AgentDeathTickService.respawnNearLeader(
                entry,
                agent,
                leader,
                new AgentDeathTickService.RespawnHooks(
                        AgentMapGatewayRuntime.map()::changeMapNear,
                        AgentMapGatewayRuntime.map()::pointBelow,
                        (respawnEntry, respawnAgent, point) ->
                                AgentMovementPoseService.teleportTo(respawnEntry, respawnAgent, point),
                        (respawnEntry, ignoredAgent) ->
                                AgentMovementStateResetService.resetEntryStateAfterTeleport(respawnEntry),
                        (respawnEntry, ignoredAgent) ->
                                AgentMovementBroadcastService.broadcastMovement(respawnEntry),
                        AgentReplyRuntime::sayMapNow));
    }
}
