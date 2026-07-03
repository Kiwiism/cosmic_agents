package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementStateResetService;

import server.agents.capabilities.movement.AgentMovementBroadcastService;

import client.Character;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.integration.AgentBotDeathStateRuntime;
import server.agents.integration.AgentBotMapStateRuntime;
import server.agents.integration.AgentBotMovementBroadcastStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotTickCadenceStateRuntime;
import server.bots.BotEntry;
import server.bots.BotMovementManager;
import server.bots.BotPhysicsEngine;
import server.maps.MapleMap;

import java.awt.Point;

/**
 * Temporary legacy hook bundle for spawn placement while movement/physics still
 * live under the reconstructed bot runtime.
 */
public final class AgentSpawnPlacementRuntime {
    private AgentSpawnPlacementRuntime() {
    }

    public static void placeSpawnedOnlineAgent(BotEntry entry, Character agent, MapleMap spawnMap, Point spawnPosition) {
        AgentSpawnPlacementService.placeSpawnedOnlineAgent(entry, agent, spawnMap, spawnPosition, hooks());
    }

    public static void normalizeSpawnedAgent(BotEntry entry) {
        AgentSpawnPlacementService.normalizeSpawnedAgent(entry, hooks());
    }

    private static AgentSpawnPlacementService.Hooks hooks() {
        return new AgentSpawnPlacementService.Hooks(
                AgentSpawnPositionService::resolveSpawnPosition,
                BotPhysicsEngine::teleportTo,
                AgentMovementStateResetService::resetEntryStateAfterTeleport,
                AgentBotDeathStateRuntime::clear,
                (entry, map, mapId) -> AgentBotMapStateRuntime.setMapTracking(
                        entry,
                        mapId,
                        map != null && map.getFootholds() != null ? BotMovementManager.buildFhIndex(map) : null),
                (entry, map) -> AgentNavigationGraphService.warmGraphAsync(
                        map,
                        AgentBotMovementStateRuntime.movementProfile(entry)),
                AgentBotTickCadenceStateRuntime::reset,
                AgentBotMovementStateRuntime::clearMoveDirection,
                AgentBotMovementBroadcastStateRuntime::invalidate,
                AgentMovementBroadcastService::broadcastMovement,
                Character::updatePartyMemberHP,
                AgentPartyLifecycleService::joinAgentToLeaderParty);
    }
}
