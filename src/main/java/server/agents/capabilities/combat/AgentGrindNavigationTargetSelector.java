package server.agents.capabilities.combat;

import client.Character;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationPathService;
import server.agents.capabilities.navigation.AgentNavigationRegionService;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;

/**
 * Agent-owned grind navigation target selector for ranged retreat, breakout, and cross-region kiting.
 */
public final class AgentGrindNavigationTargetSelector {
    @FunctionalInterface
    public interface RegionResolver {
        int resolve(AgentNavigationGraph graph, AgentRuntimeEntry entry, MapleMap map, Point position);
    }

    @FunctionalInterface
    public interface PathFinder {
        List<AgentNavigationGraph.Edge> find(AgentNavigationGraph graph,
                                             MapleMap map,
                                             Point start,
                                             int fromRegionId,
                                             int toRegionId,
                                             Point target);
    }

    public record NavigationHooks(RegionResolver currentRegionResolver,
                                  RegionResolver targetRegionResolver,
                                  PathFinder pathFinder,
                                  int grindEdgeMargin,
                                  int jumpYThreshold) {
    }

    private static final int RETREAT_HOLD_MS = 600;
    private static final int RETREAT_ARRIVAL_TOLERANCE_X = 25;

    private AgentGrindNavigationTargetSelector() {
    }

    public static Point selectGrindNavigationTarget(AgentRuntimeEntry entry,
                                                    Point agentPosition,
                                                    Point combatTargetPosition) {
        return selectGrindNavigationTarget(entry, agentPosition, combatTargetPosition, defaultHooks());
    }

    public static Point selectGrindNavigationTarget(AgentRuntimeEntry entry,
                                                    Point agentPosition,
                                                    Point combatTargetPosition,
                                                    boolean crossRegionRetreatChecked) {
        return selectGrindNavigationTarget(
                entry, agentPosition, combatTargetPosition, crossRegionRetreatChecked, defaultHooks());
    }

    public static Point selectCrossRegionRetreatTarget(AgentRuntimeEntry entry,
                                                       Point agentPosition,
                                                       Point combatTargetPosition) {
        return selectCrossRegionRetreatTarget(entry, agentPosition, combatTargetPosition, defaultHooks());
    }

    public static boolean shouldUseLocalCombatRetreatTarget(AgentRuntimeEntry entry,
                                                            Point agentPosition,
                                                            Point combatTargetPosition,
                                                            Point retreatPosition) {
        return shouldUseLocalCombatRetreatTarget(
                entry, agentPosition, combatTargetPosition, retreatPosition, defaultHooks());
    }

    private static NavigationHooks defaultHooks() {
        return new NavigationHooks(
                AgentNavigationRegionService::resolveCurrentRegionId,
                AgentNavigationRegionService::resolveTargetRegionId,
                AgentNavigationPathService::findPath,
                AgentMovementPhysicsConfig.configuredGrindEdgeMargin(),
                AgentMovementPhysicsConfig.configuredJumpYThreshold());
    }

    public static Point selectGrindNavigationTarget(AgentRuntimeEntry entry,
                                                    Point agentPosition,
                                                    Point combatTargetPosition,
                                                    NavigationHooks hooks) {
        return selectGrindNavigationTarget(entry, agentPosition, combatTargetPosition, false, hooks);
    }

    public static Point selectGrindNavigationTarget(AgentRuntimeEntry entry,
                                                    Point agentPosition,
                                                    Point combatTargetPosition,
                                                    boolean crossRegionRetreatChecked,
                                                    NavigationHooks hooks) {
        if (entry == null || agentPosition == null || combatTargetPosition == null) {
            return combatTargetPosition;
        }

        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null) {
            return combatTargetPosition;
        }

        long now = System.currentTimeMillis();
        boolean retreatNeeded = AgentAttackExecutionProvider.shouldRetreatFromNearbyTarget(
                AgentAttackExecutionProvider.getEquippedWeaponType(agent), agentPosition, combatTargetPosition);

        if (AgentBreakoutStateRuntime.hasBreakoutCommitment(entry)) {
            if (AgentBreakoutStateRuntime.isExpired(entry, now)
                    || !AgentAttackExecutionProvider.isSurrounded(agent, agentPosition)) {
                AgentBreakoutStateRuntime.clear(entry);
            } else {
                return breakoutStep(agentPosition, AgentBreakoutStateRuntime.direction(entry));
            }
        }

        if (AgentRetreatHoldStateRuntime.hasActiveHold(entry, now)) {
            int dxHold = AgentRetreatHoldStateRuntime.distanceFromHoldX(entry, agentPosition);
            if (dxHold <= RETREAT_ARRIVAL_TOLERANCE_X) {
                AgentRetreatHoldStateRuntime.clear(entry);
            } else if (dxHold > AgentCombatConfig.cfg.RANGED_RETREAT_DISTANCE_X * 2) {
                AgentRetreatHoldStateRuntime.clear(entry);
            } else {
                return AgentRetreatHoldStateRuntime.holdPosition(entry);
            }
        } else if (AgentRetreatHoldStateRuntime.hasHold(entry)) {
            AgentRetreatHoldStateRuntime.clear(entry);
        }

        if (!retreatNeeded) {
            return combatTargetPosition;
        }

        Point crossRegionPos = crossRegionRetreatChecked
                ? null
                : selectCrossRegionRetreatTarget(entry, agentPosition, combatTargetPosition, hooks);
        if (crossRegionPos != null) {
            return crossRegionPos;
        }

        if (AgentAttackExecutionProvider.isSurrounded(agent, agentPosition)) {
            int dir = pickBreakoutDirection(entry, agentPosition, combatTargetPosition, hooks);
            AgentBreakoutStateRuntime.setBreakoutCommitment(
                    entry, dir, now + AgentCombatConfig.cfg.BREAKOUT_MAX_MS);
            AgentRetreatHoldStateRuntime.clear(entry);
            return breakoutStep(agentPosition, dir);
        }

        Point retreatPos = AgentAttackExecutionProvider.retreatTargetPosition(agent, agentPosition, combatTargetPosition);
        if (shouldUseLocalCombatRetreatTarget(entry, agentPosition, combatTargetPosition, retreatPos, hooks)) {
            AgentRetreatHoldStateRuntime.setHold(entry, retreatPos, now + RETREAT_HOLD_MS);
            return retreatPos;
        }
        return combatTargetPosition;
    }

    private static Point breakoutStep(Point agentPosition, int dir) {
        return new Point(agentPosition.x + dir * AgentCombatConfig.cfg.RANGED_RETREAT_DISTANCE_X, agentPosition.y);
    }

    private static int pickBreakoutDirection(AgentRuntimeEntry entry,
                                             Point agentPosition,
                                             Point combatTargetPosition,
                                             NavigationHooks hooks) {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        int base = AgentAttackExecutionProvider.pickRetreatDirection(agent, agentPosition, combatTargetPosition);
        MapleMap map = agent != null ? agent.getMap() : null;
        if (map == null || map.getFootholds() == null) {
            return base;
        }
        AgentNavigationGraph graph = AgentNavigationGraphService.peekGraph(map, AgentMovementStateRuntime.movementProfile(entry));
        if (graph == null) {
            return base;
        }
        int agentRegionId = hooks.currentRegionResolver().resolve(graph, entry, map, agentPosition);
        if (agentRegionId < 0) {
            return base;
        }
        int leftBestMobs = Integer.MAX_VALUE;
        int rightBestMobs = Integer.MAX_VALUE;
        for (AgentNavigationGraph.Edge edge : graph.getOutgoing(agentRegionId)) {
            if (edge.type != AgentNavigationGraph.EdgeType.WALK) {
                continue;
            }
            AgentNavigationGraph.Region region = graph.getRegion(edge.toRegionId);
            if (region == null || region.isRopeRegion) {
                continue;
            }
            int side = Integer.signum(edge.endPoint.x - agentPosition.x);
            int mobs = countMobsInRegion(graph, map, region);
            if (side < 0) {
                leftBestMobs = Math.min(leftBestMobs, mobs);
            } else if (side > 0) {
                rightBestMobs = Math.min(rightBestMobs, mobs);
            }
        }
        if (leftBestMobs == rightBestMobs) {
            return base;
        }
        return leftBestMobs < rightBestMobs ? -1 : 1;
    }

    public static Point selectCrossRegionRetreatTarget(AgentRuntimeEntry entry,
                                                       Point agentPosition,
                                                       Point combatTargetPosition,
                                                       NavigationHooks hooks) {
        if (entry == null || agentPosition == null || combatTargetPosition == null) {
            return null;
        }
        if (AgentMovementStateRuntime.climbing(entry)
                || AgentMovementStateRuntime.inAir(entry)
                || AgentNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)) {
            return null;
        }
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        MapleMap map = agent != null ? agent.getMap() : null;
        if (map == null || map.getFootholds() == null) {
            return null;
        }
        AgentNavigationGraph graph = AgentNavigationGraphService.peekGraph(map, AgentMovementStateRuntime.movementProfile(entry));
        if (graph == null) {
            AgentNavigationGraphService.warmGraphAsync(map, AgentMovementStateRuntime.movementProfile(entry));
            return null;
        }

        int agentRegionId = hooks.currentRegionResolver().resolve(graph, entry, map, agentPosition);
        if (agentRegionId < 0) {
            return null;
        }
        int targetRegionId = hooks.targetRegionResolver().resolve(graph, entry, map, combatTargetPosition);

        int projectileRange = AgentProjectileHitbox.CLIENT_PROJECTILE_BASE_RANGE
                + AgentProjectileHitbox.passiveProjectileRangeBonus(agent);
        int yReachable = AgentCombatConfig.cfg.RANGED_DEGENERATE_RANGE_Y * 2;

        Point reachableRetreat = selectReachableProjectileRetreatTarget(
                graph, map, agentPosition, agentRegionId, targetRegionId,
                combatTargetPosition, projectileRange, yReachable, hooks);
        if (reachableRetreat != null) {
            return reachableRetreat;
        }

        AgentNavigationGraph.Edge bestEdge = null;
        int bestScore = Integer.MIN_VALUE;
        for (AgentNavigationGraph.Edge edge : graph.getOutgoing(agentRegionId)) {
            if (edge.type != AgentNavigationGraph.EdgeType.WALK) {
                continue;
            }
            int toRegionId = edge.toRegionId;
            if (toRegionId == agentRegionId || toRegionId == targetRegionId) {
                continue;
            }
            AgentNavigationGraph.Region region = graph.getRegion(toRegionId);
            if (region == null || region.isRopeRegion) {
                continue;
            }
            Point anchor = edge.endPoint;
            int dx = Math.abs(anchor.x - combatTargetPosition.x);
            int dy = Math.abs(anchor.y - combatTargetPosition.y);
            if (dx > projectileRange || dy > yReachable) {
                continue;
            }
            if (dx <= AgentCombatConfig.cfg.RANGED_DEGENERATE_RANGE_X) {
                continue;
            }

            int mobsInRegion = countMobsInRegion(graph, map, region);
            int score = (mobsInRegion == 0 ? 1000 : 0) - mobsInRegion * 100 - dx / 10;
            if (score > bestScore) {
                bestScore = score;
                bestEdge = edge;
            }
        }

        return bestEdge != null ? new Point(bestEdge.endPoint) : null;
    }

    private static Point selectReachableProjectileRetreatTarget(AgentNavigationGraph graph,
                                                                MapleMap map,
                                                                Point agentPosition,
                                                                int agentRegionId,
                                                                int targetRegionId,
                                                                Point combatTargetPosition,
                                                                int projectileRange,
                                                                int yReachable,
                                                                NavigationHooks hooks) {
        Point bestPoint = null;
        int bestScore = Integer.MIN_VALUE;
        for (AgentNavigationGraph.Region region : graph.regions) {
            if (region == null || region.isRopeRegion) {
                continue;
            }
            if (region.id == agentRegionId || region.id == targetRegionId) {
                continue;
            }

            Point candidate = selectProjectileRetreatPoint(region, combatTargetPosition, projectileRange, yReachable, hooks);
            if (candidate == null) {
                continue;
            }

            List<AgentNavigationGraph.Edge> path = hooks.pathFinder().find(
                    graph, map, agentPosition, agentRegionId, region.id, candidate);
            if (path.isEmpty() || pathUsesPortal(path)) {
                continue;
            }

            int pathCost = path.stream().mapToInt(pathEdge -> pathEdge.cost).sum();
            int mobsInRegion = countMobsInRegion(graph, map, region);
            int dx = Math.abs(candidate.x - combatTargetPosition.x);
            int score = (mobsInRegion == 0 ? 1500 : 0) - mobsInRegion * 150 - pathCost / 10 - dx / 10;
            if (score > bestScore) {
                bestScore = score;
                bestPoint = candidate;
            }
        }
        return bestPoint;
    }

    private static boolean pathUsesPortal(List<AgentNavigationGraph.Edge> path) {
        for (AgentNavigationGraph.Edge edge : path) {
            if (edge.type == AgentNavigationGraph.EdgeType.PORTAL) {
                return true;
            }
        }
        return false;
    }

    private static Point selectProjectileRetreatPoint(AgentNavigationGraph.Region region,
                                                      Point combatTargetPosition,
                                                      int projectileRange,
                                                      int yReachable,
                                                      NavigationHooks hooks) {
        int edgeMargin = Math.min(hooks.grindEdgeMargin(), Math.max(0, region.width() / 4));
        int minX = Math.max(region.minX + edgeMargin, combatTargetPosition.x - projectileRange);
        int maxX = Math.min(region.maxX - edgeMargin, combatTargetPosition.x + projectileRange);
        if (minX > maxX) {
            minX = Math.max(region.minX, combatTargetPosition.x - projectileRange);
            maxX = Math.min(region.maxX, combatTargetPosition.x + projectileRange);
        }
        if (minX > maxX) {
            return null;
        }

        int minShootDx = AgentCombatConfig.cfg.RANGED_DEGENERATE_RANGE_X + 20;
        int bestScore = Integer.MIN_VALUE;
        Point bestPoint = null;
        int[] probes = {
                minX,
                maxX,
                combatTargetPosition.x - minShootDx,
                combatTargetPosition.x + minShootDx,
                (minX + maxX) / 2
        };
        for (int probe : probes) {
            Point candidate = projectileRetreatCandidate(region, probe, minX, maxX,
                    combatTargetPosition, projectileRange, yReachable, minShootDx, hooks);
            if (candidate == null) {
                continue;
            }
            int dx = Math.abs(candidate.x - combatTargetPosition.x);
            int dy = Math.abs(candidate.y - combatTargetPosition.y);
            int score = -dx * 10 - dy;
            if (score > bestScore) {
                bestScore = score;
                bestPoint = candidate;
            }
        }
        return bestPoint;
    }

    private static Point projectileRetreatCandidate(AgentNavigationGraph.Region region,
                                                    int probeX,
                                                    int minX,
                                                    int maxX,
                                                    Point combatTargetPosition,
                                                    int projectileRange,
                                                    int yReachable,
                                                    int minShootDx,
                                                    NavigationHooks hooks) {
        int x = Math.max(minX, Math.min(maxX, probeX));
        if (x < combatTargetPosition.x && combatTargetPosition.x - x < minShootDx) {
            x = combatTargetPosition.x - minShootDx;
        } else if (x > combatTargetPosition.x && x - combatTargetPosition.x < minShootDx) {
            x = combatTargetPosition.x + minShootDx;
        } else if (x == combatTargetPosition.x) {
            int leftX = combatTargetPosition.x - minShootDx;
            int rightX = combatTargetPosition.x + minShootDx;
            x = leftX >= minX ? leftX : rightX;
        }
        if (x < minX || x > maxX) {
            return null;
        }

        Point point = region.pointAt(x);
        int dx = Math.abs(point.x - combatTargetPosition.x);
        int dy = Math.abs(point.y - combatTargetPosition.y);
        if (dx < minShootDx
                || dx > projectileRange
                || dy > yReachable
                || point.y > combatTargetPosition.y + hooks.jumpYThreshold()) {
            return null;
        }
        return point;
    }

    private static int countMobsInRegion(AgentNavigationGraph graph,
                                         MapleMap map,
                                         AgentNavigationGraph.Region region) {
        int count = 0;
        for (server.life.Monster m : map.getAllMonsters()) {
            if (!m.isAlive()) {
                continue;
            }
            Point mp = m.getPosition();
            if (mp == null) {
                continue;
            }
            if (mp.x < region.minX - 5 || mp.x > region.maxX + 5
                    || mp.y < region.minY - 80 || mp.y > region.maxY + 80) {
                continue;
            }
            if (graph.findRegionId(map, mp) == region.id) {
                count++;
            }
        }
        return count;
    }

    public static boolean shouldUseLocalCombatRetreatTarget(AgentRuntimeEntry entry,
                                                            Point agentPosition,
                                                            Point combatTargetPosition,
                                                            Point retreatPosition,
                                                            NavigationHooks hooks) {
        if (entry == null || agentPosition == null || combatTargetPosition == null || retreatPosition == null) {
            return false;
        }
        if (AgentMovementStateRuntime.climbing(entry)
                || AgentMovementStateRuntime.inAir(entry)
                || AgentNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)) {
            return false;
        }

        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        MapleMap map = agent != null ? agent.getMap() : null;
        if (map == null || map.getFootholds() == null) {
            return false;
        }

        AgentNavigationGraph graph = AgentNavigationGraphService.peekGraph(map, AgentMovementStateRuntime.movementProfile(entry));
        if (graph == null) {
            AgentNavigationGraphService.warmGraphAsync(map, AgentMovementStateRuntime.movementProfile(entry));
            return false;
        }
        int agentRegionId = hooks.currentRegionResolver().resolve(graph, entry, map, agentPosition);
        int combatTargetRegionId = hooks.targetRegionResolver().resolve(graph, entry, map, combatTargetPosition);
        if (agentRegionId < 0 || combatTargetRegionId < 0 || agentRegionId != combatTargetRegionId) {
            return false;
        }

        int retreatRegionId = hooks.targetRegionResolver().resolve(graph, entry, map, retreatPosition);
        return retreatRegionId == agentRegionId;
    }
}
