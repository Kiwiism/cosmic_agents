package server.bots;

import server.agents.capabilities.navigation.AgentNavigationCommittedEdgeService;
import server.agents.capabilities.navigation.AgentNavigationPathService;
import server.agents.capabilities.navigation.AgentNavigationRegionService;
import server.agents.capabilities.navigation.AgentNavigationTargetService;

import server.agents.capabilities.navigation.AgentNavigationGraph;
import client.Character;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.maps.MapleMap;

import java.awt.*;
import java.util.List;

public final class BotNavigationManager {
    public static final class NavigationDirective {
        public final Point targetPos;
        public final boolean consumedTick;

        NavigationDirective(Point targetPos, boolean consumedTick) {
            this.targetPos = targetPos;
            this.consumedTick = consumedTick;
        }
    }

    public static NavigationDirective resolveTarget(BotEntry entry, Point rawTargetPos, boolean runAiTick) {
        AgentNavigationTargetService.NavigationDirective directive =
                AgentNavigationTargetService.resolveTarget(entry, rawTargetPos, runAiTick);
        return new NavigationDirective(directive.targetPos(), directive.consumedTick());
    }

    public static boolean tryExecuteCommittedEdgeAfterGroundMovement(BotEntry entry, Point rawTargetPos) {
        return AgentNavigationTargetService.tryExecuteCommittedEdgeAfterGroundMovement(entry, rawTargetPos);
    }
    static AgentNavigationGraph.Edge reuseCommittedEdge(AgentNavigationGraph graph,
                                                      BotEntry entry,
                                                      int startRegionId,
                                                      int targetRegionId) {
        return AgentNavigationCommittedEdgeService.reuseCommittedEdge(graph, entry, startRegionId, targetRegionId);
    }

    public static List<AgentNavigationGraph.Edge> findPath(AgentNavigationGraph graph,
                                                         Character bot,
                                                         int startRegionId,
                                                         int targetRegionId,
                                                         Point targetPos) {
        return AgentNavigationPathService.findPath(graph, bot, startRegionId, targetRegionId, targetPos);
    }

    public static List<AgentNavigationGraph.Edge> findPath(AgentNavigationGraph graph,
                                                         MapleMap map,
                                                         Point startPos,
                                                         int startRegionId,
                                                         int targetRegionId,
                                                         Point targetPos) {
        return AgentNavigationPathService.findPath(graph, map, startPos, startRegionId, targetRegionId, targetPos);
    }

    public static List<AgentNavigationGraph.Edge> findPathForTargetScore(AgentNavigationGraph graph,
                                                                MapleMap map,
                                                                Point startPos,
                                                                int startRegionId,
                                                                int targetRegionId,
                                                                Point targetPos) {
        return AgentNavigationPathService.findPathForTargetScore(graph, map, startPos, startRegionId, targetRegionId, targetPos);
    }

    /**
     * Core region-graph A* search. With {@code zeroHeuristic=true} it runs an admissible h=0
     * search (degenerates to Dijkstra) that always returns the optimal-cost path; the default
     * dx-based heuristic can over-estimate across zero-cost PORTAL edges (and faster-than-walk
     * jumps) and return a longer route. {@code instrument=false} skips slow-path logging and the
     * perf record so measurement callers can run the search twice cheaply.
     */
    static SearchOutcome runSearch(AgentNavigationGraph graph,
                                   MapleMap map,
                                   Point startPos,
                                   int startRegionId,
                                   int targetRegionId,
                                   Point targetPos,
                                   String pathfindCaller,
                                   boolean zeroHeuristic,
                                   boolean instrument) {
        AgentNavigationPathService.SearchOutcome outcome = AgentNavigationPathService.runSearch(
                graph, map, startPos, startRegionId, targetRegionId, targetPos,
                pathfindCaller, zeroHeuristic, instrument);
        return new SearchOutcome(outcome.path(), outcome.cost(), outcome.expandedNodes(), outcome.usesPortal());
    }

    /** Result of a single {@link #runSearch} call. */
    record SearchOutcome(List<AgentNavigationGraph.Edge> path, int cost, int expandedNodes, boolean usesPortal) {
    }

    /** Side-by-side comparison of the production heuristic vs the admissible (h=0) optimal search. */
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

        /** True when the heuristic walked a longer route while the optimal path took a portal. */
        public boolean portalSkipped() {
            return suboptimal() && optimalUsesPortal && !currentUsesPortal;
        }
    }

    /**
     * Measurement helper: runs the same start/target search with the production heuristic and with
     * the admissible h=0 heuristic, returning both costs so callers can quantify how often (and by
     * how much) the current heuristic returns a non-optimal path. Not used on any production path.
     */
    public static PathOptimality measureOptimality(AgentNavigationGraph graph,
                                                   MapleMap map,
                                                   Point startPos,
                                                   int startRegionId,
                                                   int targetRegionId,
                                                   Point targetPos) {
        AgentNavigationPathService.PathOptimality optimality = AgentNavigationPathService.measureOptimality(
                graph, map, startPos, startRegionId, targetRegionId, targetPos);
        return new PathOptimality(
                optimality.currentCost(),
                optimality.optimalCost(),
                optimality.currentUsesPortal(),
                optimality.optimalUsesPortal(),
                optimality.currentExpanded(),
                optimality.optimalExpanded());
    }

    static boolean shouldRetainCommittedGroundEdge(AgentNavigationGraph.Edge current,
                                                   AgentNavigationGraph.Edge replacement) {
        return AgentNavigationCommittedEdgeService.shouldRetainCommittedGroundEdge(current, replacement);
    }

    public static int resolveCurrentRegionId(AgentNavigationGraph graph,
                                      BotEntry entry,
                                      MapleMap map,
                                      Point botPos) {
        return AgentNavigationRegionService.resolveCurrentRegionId(graph, entry, map, botPos);
    }

    public static int resolveTargetRegionId(AgentNavigationGraph graph,
                                     BotEntry entry,
                                     MapleMap map,
                                     Point targetPos) {
        return AgentNavigationRegionService.resolveTargetRegionId(graph, entry, map, targetPos);
    }

    public static int resolveCharacterRegionId(AgentNavigationGraph graph,
                                               MapleMap map,
                                               Character character) {
        return AgentNavigationRegionService.resolveCharacterRegionId(graph, map, character);
    }

    public static int resolvePointTargetRegionId(AgentNavigationGraph graph,
                                                 MapleMap map,
                                                 Point position) {
        return AgentNavigationRegionService.resolvePointTargetRegionId(graph, map, position);
    }

}
