package server.agents.runtime;

import client.Character;
import server.agents.capabilities.looting.AgentLootEligibility;
import server.agents.capabilities.looting.AgentLootTargetService;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.integration.AgentBotGrindLootStateRuntime;
import server.agents.integration.AgentBotGrindWanderStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotPatrolStateRuntime;
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
                                                     MapleMap map,
                                                     int lootRadius,
                                                     int stopDistance,
                                                     int grindLootRetrySuppressMs,
                                                     RegionResolver regionResolver) {
        if (entry == null || agentPosition == null) {
            return agentPosition;
        }
        if (AgentBotGrindLootStateRuntime.hasGrindLootTarget(entry)) {
            Point lootPos = activeGrindLootPosition(entry, agentPosition, lootRadius, grindLootRetrySuppressMs);
            if (lootPos != null) {
                return lootPos;
            }
        }

        AgentNavigationGraph graph = map != null
                ? AgentNavigationGraphService.peekBestGraph(map, AgentBotMovementStateRuntime.movementProfile(entry))
                : null;
        int regionId = graph != null ? regionResolver.resolve(graph, entry, map, agentPosition) : -1;
        AgentNavigationGraph.Region region = graph != null ? graph.getRegion(regionId) : null;
        if (region != null && !region.isRopeRegion && region.width() > 0) {
            Point wander = AgentBotPatrolStateRuntime.patrolWanderTarget(entry);
            if (wander == null || AgentPositionService.isNear(agentPosition, wander, stopDistance)) {
                int x = ThreadLocalRandom.current().nextInt(region.minX, region.maxX + 1);
                wander = region.pointAt(x);
                AgentBotPatrolStateRuntime.setPatrolWanderTarget(entry, wander);
            }
            return wander;
        }

        AgentBotPatrolStateRuntime.clearPatrolWanderTarget(entry);
        return new Point(
                agentPosition.x + AgentBotGrindWanderStateRuntime.ensureWanderDirection(entry) * 200,
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
        MapItem loot = AgentBotGrindLootStateRuntime.grindLootTarget(entry);
        if (loot == null || agentPosition == null) {
            AgentBotGrindLootStateRuntime.clearGrindLootTarget(entry);
            return null;
        }
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (loot.isPickedUp() || agent == null || agent.getMap() == null
                || agent.getMap().getMapObject(loot.getObjectId()) != loot) {
            AgentBotGrindLootStateRuntime.clearGrindLootTarget(entry);
            return null;
        }
        if (!AgentLootEligibility.canBotTargetLoot(entry, agent, agent.getMap(), loot, System.currentTimeMillis())) {
            AgentBotGrindLootStateRuntime.clearGrindLootTarget(entry);
            return null;
        }
        Point lootPos = loot.getPosition();
        if (Math.abs(lootPos.x - agentPosition.x) <= lootRadius
                && Math.abs(lootPos.y - agentPosition.y) <= lootRadius) {
            suppressGrindLootRetry(entry, loot, grindLootRetrySuppressMs);
            AgentBotGrindLootStateRuntime.clearGrindLootTarget(entry);
            return null;
        }
        return lootPos;
    }

    public static void suppressGrindLootRetry(AgentRuntimeEntry entry, MapItem loot, int grindLootRetrySuppressMs) {
        if (entry == null || loot == null) {
            return;
        }
        AgentBotGrindLootStateRuntime.suppressRetry(
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
                AgentBotMovementStateRuntime.movementProfile(entry));
        int patrolRegionId = AgentBotPatrolStateRuntime.patrolRegionId(entry);
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
            AgentBotPatrolStateRuntime.setPatrolWanderTarget(entry, lootTarget);
            return lootTarget;
        }
        Point wander = AgentBotPatrolStateRuntime.patrolWanderTarget(entry);
        if (wander == null || AgentPositionService.isNear(agentPosition, wander, stopDistance)) {
            int x = ThreadLocalRandom.current().nextInt(region.minX, region.maxX + 1);
            wander = region.pointAt(x);
            AgentBotPatrolStateRuntime.setPatrolWanderTarget(entry, wander);
        }
        return wander;
    }
}
