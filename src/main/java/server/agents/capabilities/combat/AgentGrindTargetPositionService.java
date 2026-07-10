package server.agents.capabilities.combat;

import server.agents.capabilities.movement.AgentPositionService;
import client.Character;
import server.agents.capabilities.looting.AgentLootEligibility;
import server.agents.capabilities.looting.AgentLootTargetService;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.looting.AgentGrindLootStateRuntime;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentPatrolStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationRegionService;
import server.agents.runtime.AgentRuntimeConfig;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.maps.MapItem;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Agent-owned grind fallback and opportunistic loot steering.
 */
public final class AgentGrindTargetPositionService {
    @FunctionalInterface
    public interface RegionResolver {
        int resolve(AgentNavigationGraph graph, AgentRuntimeEntry entry, MapleMap map, Point position);
    }

    private AgentGrindTargetPositionService() {
    }

    public static Point resolveNoGrindTargetPosition(AgentRuntimeEntry entry,
                                                     Point agentPosition,
                                                     MapleMap map) {
        return resolveNoGrindTargetPosition(
                entry,
                agentPosition,
                map,
                AgentRuntimeConfig.cfg.LOOT_RADIUS,
                AgentMovementPhysicsConfig.configuredStopDist(),
                AgentRuntimeConfig.cfg.GRIND_LOOT_RETRY_SUPPRESS_MS,
                AgentNavigationRegionService::resolveCurrentRegionId);
    }

    public static Point resolveNoGrindTargetPosition(AgentRuntimeEntry entry, Point agentPosition) {
        return resolveNoGrindTargetPosition(
                entry,
                agentPosition,
                AgentRuntimeConfig.cfg.LOOT_RADIUS,
                AgentMovementPhysicsConfig.configuredStopDist(),
                AgentRuntimeConfig.cfg.GRIND_LOOT_RETRY_SUPPRESS_MS,
                AgentNavigationRegionService::resolveCurrentRegionId);
    }

    public static Point activeGrindLootPosition(AgentRuntimeEntry entry, Point agentPosition) {
        return activeGrindLootPosition(
                entry,
                agentPosition,
                AgentRuntimeConfig.cfg.LOOT_RADIUS,
                AgentRuntimeConfig.cfg.GRIND_LOOT_RETRY_SUPPRESS_MS);
    }

    public static void suppressGrindLootRetry(AgentRuntimeEntry entry, MapItem loot) {
        suppressGrindLootRetry(entry, loot, AgentRuntimeConfig.cfg.GRIND_LOOT_RETRY_SUPPRESS_MS);
    }

    public static double activeLootTravelDistSq(Point agentPosition, Point lootPosition) {
        return activeLootTravelDistSq(agentPosition, lootPosition, AgentRuntimeConfig.cfg.LOOT_RADIUS);
    }

    public static Point convenientLootTarget(AgentRuntimeEntry entry,
                                             Point agentPosition,
                                             Point mobPosition) {
        return convenientLootTarget(
                entry,
                agentPosition,
                mobPosition,
                AgentRuntimeConfig.cfg.LOOT_RADIUS,
                AgentRuntimeConfig.cfg.GRIND_LOOT_CONVENIENCE_RATIO,
                AgentRuntimeConfig.cfg.GRIND_LOOT_RETRY_SUPPRESS_MS);
    }

    public static Point resolvePatrolWanderTarget(AgentRuntimeEntry entry,
                                                  Point agentPosition,
                                                  MapleMap map) {
        return resolvePatrolWanderTarget(
                entry,
                agentPosition,
                map,
                AgentRuntimeConfig.cfg.LOOT_RADIUS,
                AgentMovementPhysicsConfig.configuredStopDist(),
                AgentRuntimeConfig.cfg.GRIND_LOOT_RETRY_SUPPRESS_MS,
                AgentNavigationRegionService::resolveCurrentRegionId);
    }

    public static Point resolveNoGrindTargetPosition(AgentRuntimeEntry entry,
                                                     Point agentPosition,
                                                     MapleMap map,
                                                     int lootRadius,
                                                     int stopDistance,
                                                     int grindLootRetrySuppressMs,
                                                     RegionResolver regionResolver) {
        if (entry == null || agentPosition == null) {
            return agentPosition;
        }
        if (AgentGrindLootStateRuntime.hasGrindLootTarget(entry)) {
            Point lootPos = activeGrindLootPosition(entry, agentPosition, lootRadius, grindLootRetrySuppressMs);
            if (lootPos != null) {
                return lootPos;
            }
        }

        AgentNavigationGraph graph = map != null
                ? AgentNavigationGraphService.peekBestGraph(map, AgentMovementStateRuntime.movementProfile(entry))
                : null;
        int regionId = graph != null ? regionResolver.resolve(graph, entry, map, agentPosition) : -1;
        AgentNavigationGraph.Region region = graph != null ? graph.getRegion(regionId) : null;
        if (region != null && !region.isRopeRegion && region.width() > 0) {
            Point wander = AgentPatrolStateRuntime.patrolWanderTarget(entry);
            if (wander == null || AgentPositionService.isNear(agentPosition, wander, stopDistance)) {
                int x = ThreadLocalRandom.current().nextInt(region.minX, region.maxX + 1);
                wander = region.pointAt(x);
                AgentPatrolStateRuntime.setPatrolWanderTarget(entry, wander);
            }
            return wander;
        }

        AgentPatrolStateRuntime.clearPatrolWanderTarget(entry);
        return new Point(
                agentPosition.x + AgentGrindWanderStateRuntime.ensureWanderDirection(entry) * 200,
                agentPosition.y);
    }

    public static Point resolveNoGrindTargetPosition(AgentRuntimeEntry entry,
                                                     Point agentPosition,
                                                     int lootRadius,
                                                     int stopDistance,
                                                     int grindLootRetrySuppressMs,
                                                     RegionResolver regionResolver) {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        MapleMap map = agent != null ? agent.getMap() : null;
        return resolveNoGrindTargetPosition(
                entry,
                agentPosition,
                map,
                lootRadius,
                stopDistance,
                grindLootRetrySuppressMs,
                regionResolver);
    }

    public static Point activeGrindLootPosition(AgentRuntimeEntry entry,
                                                Point agentPosition,
                                                int lootRadius,
                                                int grindLootRetrySuppressMs) {
        MapItem loot = AgentGrindLootStateRuntime.grindLootTarget(entry);
        if (loot == null || agentPosition == null) {
            AgentGrindLootStateRuntime.clearGrindLootTarget(entry);
            return null;
        }
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (loot.isPickedUp() || agent == null || agent.getMap() == null
                || agent.getMap().getMapObject(loot.getObjectId()) != loot) {
            AgentGrindLootStateRuntime.clearGrindLootTarget(entry);
            return null;
        }
        if (!AgentLootEligibility.canBotTargetLoot(entry, agent, agent.getMap(), loot, System.currentTimeMillis())) {
            AgentGrindLootStateRuntime.clearGrindLootTarget(entry);
            return null;
        }
        Point lootPos = loot.getPosition();
        if (Math.abs(lootPos.x - agentPosition.x) <= lootRadius
                && Math.abs(lootPos.y - agentPosition.y) <= lootRadius) {
            suppressGrindLootRetry(entry, loot, grindLootRetrySuppressMs);
            AgentGrindLootStateRuntime.clearGrindLootTarget(entry);
            return null;
        }
        return lootPos;
    }

    public static void suppressGrindLootRetry(AgentRuntimeEntry entry, MapItem loot, int grindLootRetrySuppressMs) {
        if (entry == null || loot == null) {
            return;
        }
        AgentGrindLootStateRuntime.suppressRetry(
                entry,
                loot,
                System.currentTimeMillis() + grindLootRetrySuppressMs);
    }

    public static double activeLootTravelDistSq(Point agentPosition, Point lootPosition, int lootRadius) {
        if (agentPosition == null || lootPosition == null) {
            return Double.MAX_VALUE;
        }
        int dx = Math.max(0, Math.abs(lootPosition.x - agentPosition.x) - lootRadius);
        int dy = Math.max(0, Math.abs(lootPosition.y - agentPosition.y) - lootRadius);
        return (double) dx * dx + (double) dy * dy;
    }

    public static Point convenientLootTarget(AgentRuntimeEntry entry,
                                             Point agentPosition,
                                             Point mobPosition,
                                             int lootRadius,
                                             float grindLootConvenienceRatio,
                                             int grindLootRetrySuppressMs) {
        Point lootPos = activeGrindLootPosition(entry, agentPosition, lootRadius, grindLootRetrySuppressMs);
        if (lootPos == null) {
            return null;
        }
        double lootDistSq = activeLootTravelDistSq(agentPosition, lootPos, lootRadius);
        double mobDistSq = mobPosition.distanceSq(agentPosition);
        return lootDistSq < mobDistSq * grindLootConvenienceRatio ? lootPos : null;
    }

    public static Point resolvePatrolWanderTarget(AgentRuntimeEntry entry,
                                                  Point agentPosition,
                                                  MapleMap map,
                                                  int lootRadius,
                                                  int stopDistance,
                                                  int grindLootRetrySuppressMs,
                                                  RegionResolver regionResolver) {
        AgentNavigationGraph graph = AgentNavigationGraphService.peekBestGraph(
                map,
                AgentMovementStateRuntime.movementProfile(entry));
        int patrolRegionId = AgentPatrolStateRuntime.patrolRegionId(entry);
        AgentNavigationGraph.Region region = graph != null ? graph.getRegion(patrolRegionId) : null;
        if (region == null || region.isRopeRegion || region.width() == 0) {
            return resolveNoGrindTargetPosition(
                    entry,
                    agentPosition,
                    map,
                    lootRadius,
                    stopDistance,
                    grindLootRetrySuppressMs,
                    regionResolver);
        }
        Point lootTarget = AgentLootTargetService.findNearestPatrolLootTarget(entry, patrolRegionId);
        if (lootTarget != null) {
            AgentPatrolStateRuntime.setPatrolWanderTarget(entry, lootTarget);
            return lootTarget;
        }
        Point wander = AgentPatrolStateRuntime.patrolWanderTarget(entry);
        if (wander == null || AgentPositionService.isNear(agentPosition, wander, stopDistance)) {
            int x = ThreadLocalRandom.current().nextInt(region.minX, region.maxX + 1);
            wander = region.pointAt(x);
            AgentPatrolStateRuntime.setPatrolWanderTarget(entry, wander);
        }
        return wander;
    }
}
