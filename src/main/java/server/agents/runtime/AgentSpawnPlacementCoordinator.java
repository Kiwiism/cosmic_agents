package server.agents.runtime;

import client.Character;
import server.agents.capabilities.combat.AgentDeathStateRuntime;
import server.agents.capabilities.movement.AgentFootholdIndexService;
import server.agents.capabilities.movement.AgentMapStateRuntime;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementBroadcastStateRuntime;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.party.AgentPartyLifecycleService;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.maps.MapleMap;

import java.awt.Point;

/**
 * Runtime coordinator for spawn placement, movement-state initialization,
 * navigation warmup, broadcast, and party synchronization.
 */
public final class AgentSpawnPlacementCoordinator {
    private AgentSpawnPlacementCoordinator() {
    }

    public static void placeSpawnedOnlineAgent(AgentRuntimeEntry entry,
                                               Character agent,
                                               MapleMap spawnMap,
                                               Point spawnPosition) {
        AgentSpawnPlacementService.placeSpawnedOnlineAgent(entry, agent, spawnMap, spawnPosition, hooks());
    }

    public static void normalizeSpawnedAgent(AgentRuntimeEntry entry) {
        AgentSpawnPlacementService.normalizeSpawnedAgent(entry, hooks());
    }

    public static void normalizeSpawnedAgentWithoutParty(AgentRuntimeEntry entry) {
        AgentSpawnPlacementService.normalizeSpawnedAgent(
                entry, hooks((leader, agent) -> AgentPartyLifecycleService.leaveAgentParty(agent)));
    }

    private static AgentSpawnPlacementService.Hooks<AgentRuntimeEntry> hooks() {
        return hooks(AgentPartyLifecycleService::joinAgentToLeaderParty);
    }

    private static AgentSpawnPlacementService.Hooks<AgentRuntimeEntry> hooks(
            AgentSpawnPlacementService.LeaderPartyJoiner partyJoiner) {
        return new AgentSpawnPlacementService.Hooks<AgentRuntimeEntry>(
                AgentRuntimeIdentityRuntime::bot,
                AgentRuntimeIdentityRuntime::owner,
                AgentSpawnPositionService::resolveSpawnPosition,
                AgentMovementPoseService::teleportTo,
                AgentMovementStateResetService::resetEntryStateAfterTeleport,
                AgentDeathStateRuntime::clear,
                (entry, map, mapId) -> AgentMapStateRuntime.setMapTracking(
                        entry,
                        mapId,
                        map != null && map.getFootholds() != null ? AgentFootholdIndexService.buildFhIndex(map) : null),
                (entry, map) -> AgentNavigationGraphService.warmGraphAsync(
                        map,
                        AgentMovementStateRuntime.movementProfile(entry)),
                AgentTickCadenceStateRuntime::reset,
                AgentMovementStateRuntime::clearMoveDirection,
                AgentMovementBroadcastStateRuntime::invalidate,
                AgentMovementBroadcastService::broadcastMovement,
                Character::updatePartyMemberHP,
                partyJoiner);
    }
}
