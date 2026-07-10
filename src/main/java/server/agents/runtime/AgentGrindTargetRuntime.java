package server.agents.runtime;

import server.agents.capabilities.combat.AgentGrindTargetPositionService;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.navigation.AgentNavigationRegionService;

import server.maps.MapItem;
import server.maps.MapleMap;

import java.awt.Point;

/**
 * Temporary Agent-owned runtime bridge for grind fallback, patrol, and opportunistic loot targets.
 */
public final class AgentGrindTargetRuntime {
    private AgentGrindTargetRuntime() {
    }

    public static Point resolveNoGrindTargetPosition(AgentRuntimeEntry entry, Point agentPosition, MapleMap map) {
        return AgentGrindTargetPositionService.resolveNoGrindTargetPosition(
                entry,
                agentPosition,
                map,
                AgentRuntimeConfig.cfg.LOOT_RADIUS,
                AgentMovementPhysicsConfig.configuredStopDist(),
                AgentRuntimeConfig.cfg.GRIND_LOOT_RETRY_SUPPRESS_MS,
                AgentNavigationRegionService::resolveCurrentRegionId);
    }

    public static Point resolveNoGrindTargetPosition(AgentRuntimeEntry entry, Point agentPosition) {
        return AgentGrindTargetPositionService.resolveNoGrindTargetPosition(
                entry,
                agentPosition,
                AgentRuntimeConfig.cfg.LOOT_RADIUS,
                AgentMovementPhysicsConfig.configuredStopDist(),
                AgentRuntimeConfig.cfg.GRIND_LOOT_RETRY_SUPPRESS_MS,
                AgentNavigationRegionService::resolveCurrentRegionId);
    }

    public static Point activeGrindLootPosition(AgentRuntimeEntry entry, Point agentPosition) {
        return AgentGrindTargetPositionService.activeGrindLootPosition(
                entry,
                agentPosition,
                AgentRuntimeConfig.cfg.LOOT_RADIUS,
                AgentRuntimeConfig.cfg.GRIND_LOOT_RETRY_SUPPRESS_MS);
    }

    public static void suppressGrindLootRetry(AgentRuntimeEntry entry, MapItem loot) {
        AgentGrindTargetPositionService.suppressGrindLootRetry(
                entry,
                loot,
                AgentRuntimeConfig.cfg.GRIND_LOOT_RETRY_SUPPRESS_MS);
    }

    public static double activeLootTravelDistSq(Point agentPosition, Point lootPosition) {
        return AgentGrindTargetPositionService.activeLootTravelDistSq(
                agentPosition,
                lootPosition,
                AgentRuntimeConfig.cfg.LOOT_RADIUS);
    }

    public static Point convenientLootTarget(AgentRuntimeEntry entry, Point agentPosition, Point mobPosition) {
        return AgentGrindTargetPositionService.convenientLootTarget(
                entry,
                agentPosition,
                mobPosition,
                AgentRuntimeConfig.cfg.LOOT_RADIUS,
                AgentRuntimeConfig.cfg.GRIND_LOOT_CONVENIENCE_RATIO,
                AgentRuntimeConfig.cfg.GRIND_LOOT_RETRY_SUPPRESS_MS);
    }

    public static Point resolvePatrolWanderTarget(AgentRuntimeEntry entry, Point agentPosition, MapleMap map) {
        return AgentGrindTargetPositionService.resolvePatrolWanderTarget(
                entry,
                agentPosition,
                map,
                AgentRuntimeConfig.cfg.LOOT_RADIUS,
                AgentMovementPhysicsConfig.configuredStopDist(),
                AgentRuntimeConfig.cfg.GRIND_LOOT_RETRY_SUPPRESS_MS,
                AgentNavigationRegionService::resolveCurrentRegionId);
    }
}
