package server.agents.capabilities.recovery;

import client.Character;
import server.agents.capabilities.combat.AgentDeathTickService;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentReplyRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Assembles map, movement, and dialogue operations for Agent respawn recovery.
 */
public final class AgentRespawnCoordinator {
    private AgentRespawnCoordinator() {
    }

    public static void respawnNearLeader(AgentRuntimeEntry entry, Character agent, Character leader) {
        respawnNearLeader(
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

    static void respawnNearLeader(AgentRuntimeEntry entry,
                                  Character agent,
                                  Character leader,
                                  AgentDeathTickService.RespawnHooks hooks) {
        AgentDeathTickService.respawnNearLeader(
                entry,
                agent,
                leader,
                hooks);
    }
}
