package server.agents.capabilities.follow;

import client.Character;
import constants.game.CharacterStance;
import server.agents.capabilities.movement.AgentGroundingService;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.navigation.AgentNavigationRegionService;
import server.maps.Foothold;
import server.maps.MapleMap;

import java.awt.Point;

public final class AgentFollowTargetPositionService {
    private static final int ROPE_SEARCH_MAX_DISTANCE = 400;

    private AgentFollowTargetPositionService() {
    }

    public static Point resolve(Point followBase,
                                Character leader,
                                Point leaderPos,
                                int snapRange,
                                MapleMap map,
                                int platformEdgeInsetPx) {
        if (leader != null && CharacterStance.isClimbing(leader.getStance()) && map != null) {
            return clampedOnLeaderRegion(followBase.x, leader, leaderPos, map, platformEdgeInsetPx);
        }

        if (snapRange > 0 && map != null) {
            Point below = AgentGroundingService.findGroundPoint(map, followBase);
            Point above = AgentGroundingService.findGroundPoint(map, new Point(followBase.x, leaderPos.y - snapRange));
            boolean belowOk = below != null && Math.abs(below.y - leaderPos.y) <= snapRange;
            boolean aboveOk = above != null && Math.abs(above.y - leaderPos.y) <= snapRange;
            if (belowOk || aboveOk) {
                if (!belowOk) {
                    return above;
                }
                if (!aboveOk) {
                    return below;
                }
                return Math.abs(below.y - leaderPos.y) <= Math.abs(above.y - leaderPos.y) ? below : above;
            }
        }
        if (map != null && map.isSwim()
                && leader != null && CharacterStance.isSwimming(leader.getStance())) {
            return new Point(followBase.x, leaderPos.y);
        }
        return clampedOnLeaderRegion(followBase.x, leader, leaderPos, map, platformEdgeInsetPx);
    }

    private static Point clampedOnLeaderRegion(int targetX,
                                               Character leader,
                                               Point leaderPos,
                                               MapleMap map,
                                               int platformEdgeInsetPx) {
        if (map != null) {
            AgentNavigationGraph graph = AgentNavigationGraphService.peekGraph(map);
            if (graph != null) {
                int leaderRegionId = leader != null
                        ? AgentNavigationRegionService.resolveCharacterRegionId(graph, map, leader)
                        : graph.findRegionId(map, leaderPos);
                AgentNavigationGraph.Region leaderRegion = graph.getRegion(leaderRegionId);
                if (leaderRegion != null) {
                    if (leaderRegion.isRopeRegion) {
                        AgentNavigationGraph.Region nearestRope = findNearestRopeAtY(graph, targetX, leaderPos.y);
                        if (nearestRope == null) {
                            nearestRope = leaderRegion;
                        }
                        return new Point(nearestRope.minX, leaderPos.y);
                    }

                    int edgeMargin = platformEdgeInsetPx;
                    int minX = leaderRegion.minX;
                    int maxX = leaderRegion.maxX;
                    if (maxX - minX > 2 * edgeMargin) {
                        minX += edgeMargin;
                        maxX -= edgeMargin;
                    }
                    int clampedX = Math.max(minX, Math.min(maxX, targetX));
                    return leaderRegion.pointAt(clampedX);
                }
            }
        }

        Foothold leaderFh = AgentGroundingService.findGroundFoothold(map, leaderPos);
        if (leaderFh != null) {
            int x1 = Math.min(leaderFh.getX1(), leaderFh.getX2());
            int x2 = Math.max(leaderFh.getX1(), leaderFh.getX2());
            targetX = Math.max(x1, Math.min(x2, targetX));
        }
        Point fallback = map == null ? null : AgentGroundingService.findGroundPoint(map, new Point(targetX, leaderPos.y));
        return fallback != null ? fallback : new Point(targetX, leaderPos.y);
    }

    private static AgentNavigationGraph.Region findNearestRopeAtY(AgentNavigationGraph graph, int targetX, int targetY) {
        AgentNavigationGraph.Region nearestRope = null;
        int nearestDistance = Integer.MAX_VALUE;

        for (AgentNavigationGraph.Region region : graph.regions) {
            if (region.isRopeRegion) {
                if (region.minY > targetY || region.maxY < targetY) {
                    continue;
                }
                int ropeX = region.minX;
                int distance = Math.abs(ropeX - targetX);
                if (distance < nearestDistance && distance <= ROPE_SEARCH_MAX_DISTANCE) {
                    nearestDistance = distance;
                    nearestRope = region;
                }
            }
        }

        return nearestRope;
    }
}
