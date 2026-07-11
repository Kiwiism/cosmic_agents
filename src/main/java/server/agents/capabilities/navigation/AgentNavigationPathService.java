package server.agents.capabilities.navigation;

import client.Character;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.monitoring.AgentPerformanceMonitor;
import server.maps.MapleMap;
import server.maps.Portal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Agent-owned seam for navigation path search while the legacy search body migrates.
 */
public final class AgentNavigationPathService {
    private static final Logger log = LoggerFactory.getLogger(AgentNavigationPathService.class);
    private static final int NO_MOVEMENT_WALK_TOLERANCE = 4;
    private static final long PORTAL_USE_COOLDOWN_MS = 250L;
    private static final long SLOW_PATHFIND_WARN_NS = 50_000_000L;
    static final int MAX_EDGE_CHECKS = 160_000;

    static boolean useAdmissibleHeuristic = true;

    private AgentNavigationPathService() {
    }

    private static final class SearchNode {
        final SearchState state;
        final int cost;
        final int score;

        SearchNode(SearchState state, int cost, int score) {
            this.state = state;
            this.cost = cost;
            this.score = score;
        }
    }

    // viaPortal: true when this state was reached by a PORTAL edge. It distinguishes "arrived here
    // by teleport" from "arrived by walk/jump/etc." so the search can charge the portal cooldown to
    // a portal that chains straight off another portal.
    private record SearchState(int regionId, Point point, boolean viaPortal) {
    }

    private record PathfindProfile(long elapsedNs,
                                   int expandedNodes,
                                   int staleNodes,
                                   int edgeChecks,
                                   int usableEdges,
                                   int relaxations,
                                   int openPeak,
                                   int bestGoalCost,
                                   int resultEdges,
                                   boolean capped) {
    }

    public record SearchOutcome(List<AgentNavigationGraph.Edge> path,
                                int cost,
                                int expandedNodes,
                                boolean usesPortal,
                                boolean reached,
                                boolean capped,
                                boolean bestEffort,
                                int finalRegionId) {
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
        return findPath(graph, bot.getMap(), bot.getPosition(), startRegionId, targetRegionId, targetPos);
    }

    public static List<AgentNavigationGraph.Edge> findPath(AgentNavigationGraph graph,
                                                           MapleMap map,
                                                           Point startPos,
                                                           int startRegionId,
                                                           int targetRegionId,
                                                           Point targetPos) {
        return findPath(graph, map, startPos, startRegionId, targetRegionId, targetPos, null);
    }

    public static List<AgentNavigationGraph.Edge> findPathForTargetScore(AgentNavigationGraph graph,
                                                                         MapleMap map,
                                                                         Point startPos,
                                                                         int startRegionId,
                                                                         int targetRegionId,
                                                                         Point targetPos) {
        return findPath(graph, map, startPos, startRegionId, targetRegionId, targetPos, "target-score");
    }

    public static List<AgentNavigationGraph.Edge> findPathForRetreatProbe(AgentNavigationGraph graph,
                                                                          MapleMap map,
                                                                          Point startPos,
                                                                          int startRegionId,
                                                                          int targetRegionId,
                                                                          Point targetPos) {
        return runSearch(graph, map, startPos, startRegionId, targetRegionId, targetPos,
                "retreat-probe", false, true).path();
    }

    public static List<AgentNavigationGraph.Edge> findPathForApproachProbe(AgentNavigationGraph graph,
                                                                           MapleMap map,
                                                                           Point startPos,
                                                                           int startRegionId,
                                                                           int targetRegionId,
                                                                           Point targetPos) {
        return runSearch(graph, map, startPos, startRegionId, targetRegionId, targetPos,
                "approach-probe", false, true).path();
    }

    public static AgentNavigationGraph.Edge findNextEdge(AgentNavigationGraph graph,
                                                         Character bot,
                                                         int startRegionId,
                                                         int targetRegionId,
                                                         Point targetPos) {
        List<AgentNavigationGraph.Edge> path = findPath(graph, bot.getMap(), bot.getPosition(), startRegionId, targetRegionId, targetPos);
        if (path.isEmpty()) {
            return null;
        }
        return collapseLeadingWalkEdges(path);
    }

    public static List<AgentNavigationGraph.Edge> findPath(AgentNavigationGraph graph,
                                                           MapleMap map,
                                                           Point startPos,
                                                           int startRegionId,
                                                           int targetRegionId,
                                                           Point targetPos,
                                                           String pathfindCaller) {
        return runSearch(graph, map, startPos, startRegionId, targetRegionId, targetPos,
                pathfindCaller, useAdmissibleHeuristic, true).path();
    }

    public static List<AgentNavigationGraph.Edge> findPath(AgentNavigationGraph graph,
                                                           MapleMap map,
                                                           Point startPos,
                                                           int startRegionId,
                                                           int targetRegionId,
                                                           Point targetPos,
                                                           String pathfindCaller,
                                                           boolean zeroHeuristic,
                                                           boolean instrument) {
        return runSearch(graph, map, startPos, startRegionId, targetRegionId, targetPos,
                pathfindCaller, zeroHeuristic, instrument).path();
    }

    public static SearchOutcome runSearch(AgentNavigationGraph graph,
                                          MapleMap map,
                                          Point startPos,
                                          int startRegionId,
                                          int targetRegionId,
                                          Point targetPos,
                                          String pathfindCaller,
                                          boolean zeroHeuristic,
                                          boolean instrument) {
        return runSearch(graph, map, startPos, startRegionId, targetRegionId, targetPos,
                pathfindCaller, zeroHeuristic, instrument, MAX_EDGE_CHECKS);
    }

    static SearchOutcome runSearch(AgentNavigationGraph graph,
                                   MapleMap map,
                                   Point startPos,
                                   int startRegionId,
                                   int targetRegionId,
                                   Point targetPos,
                                   String pathfindCaller,
                                   boolean zeroHeuristic,
                                   boolean instrument,
                                   int edgeCheckBudget) {
        long startedAt = System.nanoTime();
        PathfindProfile profile = null;
        try {
            int startComponent = graph.connectedComponentId(startRegionId);
            int targetComponent = graph.connectedComponentId(targetRegionId);
            if (startComponent < 0 || targetComponent < 0 || startComponent != targetComponent) {
                return new SearchOutcome(List.of(), Integer.MAX_VALUE, 0, false,
                        false, false, false, startRegionId);
            }

            PriorityQueue<SearchNode> open = new PriorityQueue<>(Comparator.comparingInt(node -> node.score));
            Map<SearchState, Integer> gScore = new HashMap<>();
            Map<SearchState, SearchState> cameFrom = new HashMap<>();
            Map<SearchState, AgentNavigationGraph.Edge> cameByEdge = new HashMap<>();
            SearchState startState = new SearchState(startRegionId, new Point(startPos), false);
            SearchState bestGoalState = null;
            SearchState closestState = startState;
            long closestDistance = rawDistance(startPos, targetPos);
            int bestGoalCost = Integer.MAX_VALUE;
            int expandedNodes = 0;
            int staleNodes = 0;
            int edgeChecks = 0;
            int usableEdges = 0;
            int relaxations = 0;
            int openPeak = 1;
            boolean capped = false;

            gScore.put(startState, 0);
            open.add(new SearchNode(startState, 0, zeroHeuristic ? 0 : heuristic(graph, startPos, targetPos)));

            while (!open.isEmpty()) {
                SearchNode current = open.poll();
                if (current.cost != gScore.getOrDefault(current.state, Integer.MAX_VALUE)) {
                    staleNodes++;
                    continue;
                }
                if (bestGoalState != null && current.score >= bestGoalCost) {
                    break;
                }
                expandedNodes++;

                if (current.state.regionId == targetRegionId) {
                    int goalCost = current.cost + intraRegionTravelCost(graph, current.state.regionId, current.state.point, targetPos);
                    if (goalCost < bestGoalCost) {
                        bestGoalCost = goalCost;
                        bestGoalState = current.state;
                    }
                }

                for (AgentNavigationGraph.Edge edge : graph.getOutgoing(current.state.regionId)) {
                    edgeChecks++;
                    if (edgeChecks > Math.max(1, edgeCheckBudget)) {
                        capped = true;
                        break;
                    }
                    if (!isEdgeUsable(graph, map, edge)) {
                        continue;
                    }
                    usableEdges++;

                    boolean isPortal = edge.type == AgentNavigationGraph.EdgeType.PORTAL;
                    // Portals are free on their own (edge.cost == 0). Charge the portal cooldown
                    // only when the bot enters a portal through the exit of the one it just took.
                    boolean enteredThroughExit = current.state.viaPortal
                            && current.state.point.equals(edge.startPoint);
                    int edgeCost = isPortal && enteredThroughExit ? (int) PORTAL_USE_COOLDOWN_MS : edge.cost;
                    int tentativeCost = current.cost + intraRegionTravelCost(graph, current.state.regionId, current.state.point, edge.startPoint) + edgeCost;
                    SearchState nextState = new SearchState(edge.toRegionId, edge.endPoint, isPortal);
                    if (tentativeCost >= gScore.getOrDefault(nextState, Integer.MAX_VALUE)) {
                        continue;
                    }

                    relaxations++;
                    gScore.put(nextState, tentativeCost);
                    cameFrom.put(nextState, current.state);
                    cameByEdge.put(nextState, edge);
                    int fScore = tentativeCost + (zeroHeuristic ? 0 : heuristic(graph, edge.endPoint, targetPos));
                    open.add(new SearchNode(nextState, tentativeCost, fScore));
                    openPeak = Math.max(openPeak, open.size());
                    long reachedDistance = rawDistance(edge.endPoint, targetPos);
                    if (reachedDistance < closestDistance) {
                        closestDistance = reachedDistance;
                        closestState = nextState;
                    }
                }
                if (capped) {
                    break;
                }
            }

            SearchState resultState = bestGoalState;
            if (resultState == null && capped && bestEffortCaller(pathfindCaller)
                    && !closestState.equals(startState)) {
                resultState = closestState;
            }
            List<AgentNavigationGraph.Edge> path = reconstructPath(startState, resultState, cameFrom, cameByEdge);
            profile = new PathfindProfile(
                    System.nanoTime() - startedAt,
                    expandedNodes,
                    staleNodes,
                    edgeChecks,
                    usableEdges,
                    relaxations,
                    openPeak,
                    bestGoalCost,
                    path.size(),
                    capped);
            boolean usesPortal = false;
            for (AgentNavigationGraph.Edge edge : path) {
                if (edge.type == AgentNavigationGraph.EdgeType.PORTAL) {
                    usesPortal = true;
                    break;
                }
            }
            int finalRegionId = resultState == null ? startRegionId : resultState.regionId;
            boolean reached = resultState != null && finalRegionId == targetRegionId;
            return new SearchOutcome(path, bestGoalCost, expandedNodes, usesPortal,
                    reached, capped, !reached && !path.isEmpty(), finalRegionId);
        } finally {
            if (instrument) {
                if (profile == null) {
                    profile = new PathfindProfile(
                            System.nanoTime() - startedAt,
                            0,
                            0,
                            0,
                            0,
                            0,
                            0,
                            Integer.MAX_VALUE,
                            0,
                            false);
                }
                logSlowPathfind(graph, map, startPos, startRegionId, targetRegionId, targetPos, pathfindCaller, profile);
                AgentPerformanceMonitor.recordPathfind(pathfindCaller, System.nanoTime() - startedAt);
            }
        }
    }

    private static boolean bestEffortCaller(String caller) {
        return caller == null || caller.isBlank() || "committed".equals(caller);
    }

    private static long rawDistance(Point from, Point to) {
        if (from == null || to == null) {
            return Long.MAX_VALUE;
        }
        long dx = (long) to.x - from.x;
        long dy = (long) to.y - from.y;
        return dx * dx + dy * dy;
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

    public static AgentNavigationGraph.Edge collapseLeadingWalkEdges(List<AgentNavigationGraph.Edge> path) {
        AgentNavigationGraph.Edge first = path.get(0);
        if (first.type != AgentNavigationGraph.EdgeType.WALK) {
            return first;
        }

        if (!isNoMovementWalk(first.startPoint, first.endPoint)) {
            return first;
        }

        int totalCost = 0;
        int walkCount = 0;
        while (walkCount < path.size()) {
            AgentNavigationGraph.Edge edge = path.get(walkCount);
            if (edge.type != AgentNavigationGraph.EdgeType.WALK
                    || !isNoMovementWalk(edge.startPoint, edge.endPoint)) {
                break;
            }
            totalCost += edge.cost;
            walkCount++;
        }

        if (walkCount >= path.size()) {
            return null;
        }

        AgentNavigationGraph.Edge next = path.get(walkCount);
        return new AgentNavigationGraph.Edge(first.fromRegionId, next.toRegionId, next.type,
                next.startPoint, next.endPoint, next.launchMinX, next.launchMaxX, next.launchStepX, next.portalId,
                next.ropeX, next.ropeTopY, next.ropeBottomY, totalCost + next.cost);
    }

    public static boolean shouldUsePreciseWalkTarget(AgentNavigationGraph.Edge edge) {
        return edge != null
                && edge.type == AgentNavigationGraph.EdgeType.WALK
                && !isNoMovementWalk(edge.startPoint, edge.endPoint);
    }

    public static boolean isEdgeUsable(AgentNavigationGraph graph, Character bot, AgentNavigationGraph.Edge edge) {
        return isEdgeUsable(graph, bot.getMap(), edge);
    }

    public static boolean isEdgeUsable(AgentNavigationGraph graph, MapleMap map, AgentNavigationGraph.Edge edge) {
        return switch (edge.type) {
            case WALK, JUMP, DROP, CLIMB -> true;
            case PORTAL -> {
                Portal portal = map.getPortal(edge.portalId);
                yield portal != null && portal.getPortalStatus();
            }
        };
    }

    private static boolean isNoMovementWalk(Point start, Point end) {
        return Math.abs(end.x - start.x) <= NO_MOVEMENT_WALK_TOLERANCE
                && Math.abs(end.y - start.y) <= NO_MOVEMENT_WALK_TOLERANCE;
    }

    public static PathOptimality measureOptimality(AgentNavigationGraph graph,
                                                   MapleMap map,
                                                   Point startPos,
                                                   int startRegionId,
                                                   int targetRegionId,
                                                   Point targetPos) {
        SearchOutcome current = runSearch(graph, map, startPos, startRegionId, targetRegionId, targetPos,
                "measure", false, false);
        SearchOutcome optimal = runSearch(graph, map, startPos, startRegionId, targetRegionId, targetPos,
                "measure", true, false);
        return new PathOptimality(current.cost(), optimal.cost(), current.usesPortal(),
                optimal.usesPortal(), current.expandedNodes(), optimal.expandedNodes());
    }

    private static void logSlowPathfind(AgentNavigationGraph graph,
                                        MapleMap map,
                                        Point startPos,
                                        int startRegionId,
                                        int targetRegionId,
                                        Point targetPos,
                                        String pathfindCaller,
                                        PathfindProfile profile) {
        if (profile.elapsedNs() < SLOW_PATHFIND_WARN_NS) {
            return;
        }
        int regionCount = graph != null && graph.regions != null ? graph.regions.size() : -1;
        int outgoingFromStart = graph != null ? graph.getOutgoing(startRegionId).size() : -1;
        String caller = pathfindCaller == null || pathfindCaller.isBlank() ? "default" : pathfindCaller;
        int bestGoalCost = profile.bestGoalCost() == Integer.MAX_VALUE ? -1 : profile.bestGoalCost();
        log.warn(
                "Slow bot pathfind: caller={} took {} ms map={} startRegion={} targetRegion={} regions={} startOut={} startPos=({}, {}) targetPos=({}, {}) expanded={} stale={} edgeChecks={} usableEdges={} relaxations={} openPeak={} bestGoalCost={} resultEdges={} capped={}",
                caller,
                String.format("%.1f", profile.elapsedNs() / 1_000_000.0),
                map != null ? map.getId() : -1,
                startRegionId,
                targetRegionId,
                regionCount,
                outgoingFromStart,
                startPos != null ? startPos.x : -1,
                startPos != null ? startPos.y : -1,
                targetPos != null ? targetPos.x : -1,
                targetPos != null ? targetPos.y : -1,
                profile.expandedNodes(),
                profile.staleNodes(),
                profile.edgeChecks(),
                profile.usableEdges(),
                profile.relaxations(),
                profile.openPeak(),
                bestGoalCost,
                profile.resultEdges(),
                profile.capped());
    }

    private static List<AgentNavigationGraph.Edge> reconstructPath(SearchState startState,
                                                                   SearchState goalState,
                                                                   Map<SearchState, SearchState> cameFrom,
                                                                   Map<SearchState, AgentNavigationGraph.Edge> cameByEdge) {
        if (goalState == null || !cameByEdge.containsKey(goalState)) {
            return List.of();
        }

        List<AgentNavigationGraph.Edge> path = new ArrayList<>();
        SearchState cursor = goalState;
        while (!cursor.equals(startState)) {
            AgentNavigationGraph.Edge edge = cameByEdge.get(cursor);
            if (edge == null) {
                return List.of();
            }

            path.add(0, edge);
            SearchState previousState = cameFrom.get(cursor);
            if (previousState == null) {
                return List.of();
            }
            cursor = previousState;
        }
        return path;
    }
}
