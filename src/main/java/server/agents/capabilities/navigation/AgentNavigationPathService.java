package server.agents.capabilities.navigation;

import client.Character;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.bots.BotNavigationManager;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;

/**
 * Agent-owned seam for navigation path search while the legacy search body migrates.
 */
public final class AgentNavigationPathService {
    private AgentNavigationPathService() {
    }

    public record PathOptimality(int currentCost, int optimalCost, boolean currentUsesPortal,
                                 boolean optimalUsesPortal, int currentExpanded, int optimalExpanded) {
        public boolean reachable() {
            return currentCost != Integer.MAX_VALUE && optimalCost != Integer.MAX_VALUE;
        }

        public boolean suboptimal() {
            return reachable() && currentCost > optimalCost;
        }

        public int costDelta() {
            return reachable() ? currentCost - optimalCost : 0;
        }

        public boolean portalSkipped() {
            return suboptimal() && optimalUsesPortal && !currentUsesPortal;
        }
    }

    public static List<AgentNavigationGraph.Edge> findPath(AgentNavigationGraph graph,
                                                           Character bot,
                                                           int startRegionId,
                                                           int targetRegionId,
                                                           Point targetPos) {
        return BotNavigationManager.findPath(graph, bot, startRegionId, targetRegionId, targetPos);
    }

    public static List<AgentNavigationGraph.Edge> findPath(AgentNavigationGraph graph,
                                                           MapleMap map,
                                                           Point startPos,
                                                           int startRegionId,
                                                           int targetRegionId,
                                                           Point targetPos) {
        return BotNavigationManager.findPath(graph, map, startPos, startRegionId, targetRegionId, targetPos);
    }

    public static List<AgentNavigationGraph.Edge> findPathForTargetScore(AgentNavigationGraph graph,
                                                                         MapleMap map,
                                                                         Point startPos,
                                                                         int startRegionId,
                                                                         int targetRegionId,
                                                                         Point targetPos) {
        return BotNavigationManager.findPathForTargetScore(graph, map, startPos, startRegionId, targetRegionId, targetPos);
    }

    public static int intraRegionTravelCost(AgentNavigationGraph graph, Point from, Point to) {
        int dx = Math.abs(to.x - from.x);
        return Math.max(0, (int) Math.round((dx * 1000.0) / Math.max(1.0, graph.movementProfile.walkVelocityPxs())));
    }

    public static int intraRegionTravelCost(AgentNavigationGraph graph, int regionId, Point from, Point to) {
        AgentNavigationGraph.Region region = graph.getRegion(regionId);
        if (region != null && region.isRopeRegion) {
            int travel = Math.abs(to.y - from.y);
            return Math.max(0, (int) Math.round((travel * 1000.0) / Math.max(1, AgentMovementPhysicsConfig.configuredClimbSpeedPxs())));
        }
        return intraRegionTravelCost(graph, from, to);
    }

    public static int heuristic(AgentNavigationGraph graph, Point from, Point targetPos) {
        return intraRegionTravelCost(graph, from, targetPos);
    }

    public static PathOptimality measureOptimality(AgentNavigationGraph graph,
                                                   MapleMap map,
                                                   Point startPos,
                                                   int startRegionId,
                                                   int targetRegionId,
                                                   Point targetPos) {
        BotNavigationManager.PathOptimality legacy = BotNavigationManager.measureOptimality(
                graph, map, startPos, startRegionId, targetRegionId, targetPos);
        return new PathOptimality(
                legacy.currentCost(),
                legacy.optimalCost(),
                legacy.currentUsesPortal(),
                legacy.optimalUsesPortal(),
                legacy.currentExpanded(),
                legacy.optimalExpanded());
    }
}
