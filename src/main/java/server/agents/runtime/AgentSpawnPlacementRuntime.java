package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementStateResetService;

import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentFootholdIndexService;
import server.agents.capabilities.movement.AgentMovementPoseService;

import client.Character;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.movement.AgentMovementBroadcastStateRuntime;
import server.agents.integration.AgentMovementStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentTickCadenceStateRuntime;
import server.maps.MapleMap;

import java.awt.Point;

/**
 * Temporary legacy hook bundle for spawn placement while movement/physics still
 * live under the reconstructed bot runtime.
 */
public final class AgentSpawnPlacementRuntime {
    private AgentSpawnPlacementRuntime() {
    }

    public static void placeSpawnedOnlineAgent(AgentRuntimeEntry entry, Character agent, MapleMap spawnMap, Point spawnPosition) {
        AgentSpawnPlacementService.placeSpawnedOnlineAgent(entry, agent, spawnMap, spawnPosition, hooks());
    }

    public static void normalizeSpawnedAgent(AgentRuntimeEntry entry) {
        AgentSpawnPlacementService.normalizeSpawnedAgent(entry, hooks());
    }

    private static AgentSpawnPlacementService.Hooks<AgentRuntimeEntry> hooks() {
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
                AgentPartyLifecycleService::joinAgentToLeaderParty);
    }
}
