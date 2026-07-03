package server.bots;

import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.navigation.AgentNavigationCommittedEdgeService;
import server.agents.capabilities.navigation.AgentNavigationEdgeExecutor;
import server.agents.capabilities.navigation.AgentNavigationEdgeExecutionStateService;
import server.agents.capabilities.navigation.AgentNavigationEdgeReadinessService;
import server.agents.capabilities.navigation.AgentNavigationGrindTargetService;
import server.agents.capabilities.navigation.AgentNavigationLaunchWindowService;
import server.agents.capabilities.navigation.AgentNavigationPhysicsService;
import server.agents.capabilities.navigation.AgentNavigationPathService;
import server.agents.capabilities.navigation.AgentNavigationPreciseTargetService;
import server.agents.capabilities.navigation.AgentNavigationRegionService;
import server.agents.capabilities.navigation.AgentNavigationRopeEdgeService;
import server.agents.capabilities.navigation.AgentNavigationTargetService;
import server.agents.capabilities.navigation.AgentNavigationWaypointService;

import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.movement.AgentGroundCollisionService;
import server.agents.capabilities.movement.AgentMovementKinematicsService;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentMovementProfile;

import client.Character;
import constants.game.CharacterStance;
import server.agents.integration.AgentBotFarmAnchorStateRuntime;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.integration.AgentBotSessionLifecycleSideEffects;
import server.agents.integration.AgentBotShopStateRuntime;
import server.agents.runtime.AgentFollowAnchorService;
import server.maps.MapleMap;
import server.maps.Foothold;
import server.maps.Rope;

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
    private static AgentNavigationGraph.Edge refreshPendingClimbExitEdge(AgentNavigationGraph graph,
                                                                       BotEntry entry,
                                                                       Character bot,
                                                                       Point botPos,
                                                                       int startRegionId,
                                                                       int targetRegionId,
                                                                       Point targetPos,
                                                                       AgentNavigationGraph.Edge edge,
                                                                       boolean runAiTick) {
        return AgentNavigationCommittedEdgeService.refreshPendingClimbExitEdge(graph, entry, bot, botPos,
                startRegionId, targetRegionId, targetPos, edge, runAiTick,
                (activeGraph, activeBot, activeBotPos, activeEdge) ->
                        canExecuteClimbExitFromCurrentPosition(activeGraph, activeBot.getMap(), activeBotPos, activeEdge),
                BotNavigationManager::findNextEdge);
    }

    private static AgentNavigationGraph.Edge refreshCommittedGroundEdge(AgentNavigationGraph graph,
                                                                      BotEntry entry,
                                                                      Character bot,
                                                                      int startRegionId,
                                                                      int targetRegionId,
                                                                      Point targetPos,
                                                                      AgentNavigationGraph.Edge edge,
                                                                      boolean runAiTick) {
        return AgentNavigationCommittedEdgeService.refreshCommittedGroundEdge(graph, entry, bot,
                startRegionId, targetRegionId, targetPos, edge, runAiTick, BotNavigationManager::findNextEdge);
    }

    static AgentNavigationGraph.Edge reuseCommittedEdge(AgentNavigationGraph graph,
                                                      BotEntry entry,
                                                      int startRegionId,
                                                      int targetRegionId) {
        return AgentNavigationCommittedEdgeService.reuseCommittedEdge(graph, entry, startRegionId, targetRegionId,
                BotNavigationManager::isEdgeUsable, BotNavigationManager::isRopeEntryEdge);
    }

    private static NavigationDirective tryExecuteEdge(AgentNavigationGraph graph,
                                                      BotEntry entry,
                                                      Character bot,
                                                      Point botPos,
                                                      Point rawTargetPos,
                                                      AgentNavigationGraph.Edge edge,
                                                      boolean runAiTick) {
        AgentNavigationEdgeExecutor.NavigationDirective directive = AgentNavigationEdgeExecutor.tryExecuteEdge(
                graph, entry, bot, botPos, rawTargetPos, edge, runAiTick);
        if (directive == null) {
            return null;
        }
        return new NavigationDirective(directive.targetPos(), directive.consumedTick());
    }

    static boolean canExecuteDropFromCurrentPosition(AgentNavigationGraph graph,
                                                     MapleMap map,
                                                     Point botPos,
                                                     AgentNavigationGraph.Edge edge) {
        return AgentNavigationEdgeReadinessService.canExecuteDropFromCurrentPosition(graph, botPos, edge);
    }

    private static boolean shouldUsePreciseTarget(AgentNavigationGraph graph,
                                                  BotEntry entry,
                                                  Point botPos,
                                                  AgentNavigationGraph.Edge edge) {
        return AgentNavigationPreciseTargetService.shouldUsePreciseTarget(
                graph,
                entry,
                botPos,
                edge,
                new AgentNavigationPreciseTargetService.EdgeReadiness() {
                    @Override
                    public boolean canExecuteSelectedJump(AgentNavigationGraph readinessGraph,
                                                          BotEntry readinessEntry,
                                                          Point readinessBotPos,
                                                          AgentNavigationGraph.Edge readinessEdge) {
                        return canExecuteSelectedJumpFromCurrentPosition(
                                readinessGraph,
                                readinessEntry,
                                AgentBotRuntimeIdentityRuntime.botMap(readinessEntry),
                                readinessBotPos,
                                readinessEdge);
                    }

                    @Override
                    public boolean canExecuteDrop(AgentNavigationGraph readinessGraph,
                                                  BotEntry readinessEntry,
                                                  Point readinessBotPos,
                                                  AgentNavigationGraph.Edge readinessEdge) {
                        return canExecuteDropFromCurrentPosition(
                                readinessGraph,
                                AgentBotRuntimeIdentityRuntime.botMap(readinessEntry),
                                readinessBotPos,
                                readinessEdge);
                    }

                    @Override
                    public boolean canExecuteClimbExit(AgentNavigationGraph readinessGraph,
                                                       BotEntry readinessEntry,
                                                       Point readinessBotPos,
                                                       AgentNavigationGraph.Edge readinessEdge) {
                        return canExecuteClimbExitFromCurrentPosition(
                                readinessGraph,
                                AgentBotRuntimeIdentityRuntime.botMap(readinessEntry),
                                readinessBotPos,
                                readinessEdge);
                    }

                    @Override
                    public boolean canExecuteClimbEntry(AgentNavigationGraph readinessGraph,
                                                        BotEntry readinessEntry,
                                                        Point readinessBotPos,
                                                        AgentNavigationGraph.Edge readinessEdge) {
                        return canExecuteClimbEntryFromCurrentPosition(
                                AgentBotRuntimeIdentityRuntime.botMap(readinessEntry),
                                readinessBotPos,
                                readinessEdge,
                                findRopeForRegion(AgentBotRuntimeIdentityRuntime.botMap(readinessEntry),
                                        readinessGraph.getRegion(readinessEdge.toRegionId)));
                    }
                });
    }

    private static Point selectWaypoint(BotEntry entry, AgentNavigationGraph graph, Point botPos, AgentNavigationGraph.Edge edge) {
        return switch (edge.type) {
            case WALK -> new Point(edge.endPoint);
            case CLIMB -> selectClimbWaypoint(graph, entry, botPos, edge);
            case JUMP -> AgentBotMovementStateRuntime.inAir(entry) ? new Point(edge.endPoint) : selectJumpWaypoint(graph, entry, botPos, edge);
            case DROP -> selectDropWaypoint(entry, graph, botPos, edge);
            case PORTAL -> AgentBotMovementStateRuntime.inAir(entry) ? new Point(edge.endPoint) : new Point(edge.startPoint);
        };
    }

    static Point selectJumpWaypoint(BotEntry entry, Point botPos, AgentNavigationGraph.Edge edge) {
        AgentNavigationGraph graph = AgentNavigationGraphService.getGraph(AgentBotRuntimeIdentityRuntime.botMap(entry), AgentBotMovementStateRuntime.movementProfile(entry));
        return selectJumpWaypoint(graph, entry, botPos, edge);
    }

    static Point selectJumpWaypoint(AgentNavigationGraph graph, Point botPos, AgentNavigationGraph.Edge edge) {
        return selectJumpWaypoint(graph, null, botPos, edge);
    }

    private static Point selectJumpWaypoint(AgentNavigationGraph graph,
                                            BotEntry entry,
                                            Point botPos,
                                            AgentNavigationGraph.Edge edge) {
        if (entry == null) {
            return AgentNavigationWaypointService.selectJumpWaypoint(graph, botPos, edge);
        }
        AgentNavigationGraph.Region fromRegion = graph.getRegion(edge.fromRegionId);
        if (fromRegion == null || fromRegion.isRopeRegion) {
            return new Point(edge.startPoint);
        }
        int targetX = selectedJumpLaunchX(entry, graph, edge);
        return fromRegion.pointAt(targetX);
    }

    static Point selectClimbWaypoint(BotEntry entry, Point botPos, AgentNavigationGraph.Edge edge) {
        AgentNavigationGraph graph = resolveActiveGraph(AgentBotRuntimeIdentityRuntime.botMap(entry), AgentBotMovementStateRuntime.movementProfile(entry));
        return selectClimbWaypoint(graph, entry, botPos, edge);
    }

    static Point selectClimbWaypoint(AgentNavigationGraph graph, BotEntry entry, Point botPos, AgentNavigationGraph.Edge edge) {
        return AgentNavigationWaypointService.selectClimbWaypoint(
                graph,
                entry,
                botPos,
                edge,
                (readinessGraph, readinessEntry, readinessBotPos, readinessEdge) ->
                        canExecuteClimbExitFromCurrentPosition(
                                readinessGraph,
                                AgentBotRuntimeIdentityRuntime.botMap(readinessEntry),
                                readinessBotPos,
                                readinessEdge));
    }

    private static AgentNavigationGraph resolveActiveGraph(MapleMap map, AgentMovementProfile movementProfile) {
        return AgentNavigationGraphService.peekBestGraph(map, movementProfile);
    }

    static Point selectDropWaypoint(BotEntry entry,
                                    AgentNavigationGraph graph,
                                    Point botPos,
                                    AgentNavigationGraph.Edge edge) {
        if (AgentBotMovementStateRuntime.inAir(entry)) {
            return new Point(edge.endPoint);
        }
        if (edge.launchStepX == 0) {
            return AgentNavigationWaypointService.selectStraightDropWaypoint(graph, botPos, edge);
        }

        if (hasReachedDirectionalDropRunway(botPos, edge)) {
            return new Point(edge.endPoint);
        }

        AgentNavigationGraph.Region fromRegion = graph.getRegion(edge.fromRegionId);
        if (fromRegion == null || fromRegion.isRopeRegion) {
            return new Point(edge.endPoint);
        }

        BotPhysicsEngine.WalkOffLanding liveOutcome = BotPhysicsEngine.simulateWalkOffLanding(
                AgentBotRuntimeIdentityRuntime.botMap(entry), botPos, Integer.signum(edge.launchStepX),
                AgentBotMovementPhysicsStateRuntime.groundTravelState(entry),
                AgentBotMovementStateRuntime.movementProfile(entry));
        if (matchesDirectionalDrop(edge, graph, liveOutcome)) {
            // Like rope top step-offs, once the continuous-control exit is naturally executable
            // we stop targeting an intermediate anchor and just keep feeding the authored
            // direction until physics performs the dismount.
            return new Point(edge.endPoint);
        }
        return new Point(edge.startPoint);
    }

    private static boolean hasReachedDirectionalDropRunway(Point botPos, AgentNavigationGraph.Edge edge) {
        return AgentNavigationLaunchWindowService.hasReachedDirectionalDropRunway(botPos, edge);
    }

    private static boolean matchesDirectionalDrop(AgentNavigationGraph.Edge edge,
                                                  AgentNavigationGraph graph,
                                                  BotPhysicsEngine.WalkOffLanding outcome) {
        if (outcome == null || outcome.landing() == null) {
            return false;
        }
        Foothold landingFoothold = outcome.landing().foothold();
        if (landingFoothold == null) {
            return false;
        }
        if (graph.regionIdByFootholdId.getOrDefault(landingFoothold.getId(), -1) != edge.toRegionId) {
            return false;
        }
        int xTolerance = Math.max(6, Math.abs(edge.launchStepX) + 2);
        int yTolerance = AgentMovementPhysicsConfig.configuredJumpYThreshold() * 2;
        return Math.abs(outcome.landing().point().x - edge.endPoint.x) <= xTolerance
                && Math.abs(outcome.landing().point().y - edge.endPoint.y) <= yTolerance;
    }

    private static AgentNavigationGraph.Edge findNextEdge(AgentNavigationGraph graph,
                                                        Character bot,
                                                        int startRegionId,
                                                        int targetRegionId,
                                                        Point targetPos) {
        return AgentNavigationPathService.findNextEdge(graph, bot, startRegionId, targetRegionId, targetPos);
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
     * Production pathfinding heuristic toggle. When {@code true} (default) the search runs the
     * admissible h=0 (Dijkstra) variant: optimal-cost paths, no portal-skipping. Flip to
     * {@code false} to restore the legacy dx/walk-speed heuristic (faster per search, but on
     * Kerning City ~19% of cross-region paths were non-optimal and ~7% walked past a usable
     * portal — see {@code BotNavigationProbe --measure}). The legacy {@link #heuristic} and the
     * {@link #runSearch} zeroHeuristic branch are both retained; this is the single knob.
     */
    static boolean useAdmissibleHeuristic = true;

    private static List<AgentNavigationGraph.Edge> findPath(AgentNavigationGraph graph,
                                                          MapleMap map,
                                                          Point startPos,
                                                          int startRegionId,
                                                          int targetRegionId,
                                                          Point targetPos,
                                                          String pathfindCaller) {
        return AgentNavigationPathService.findPath(graph, map, startPos, startRegionId, targetRegionId, targetPos,
                pathfindCaller, useAdmissibleHeuristic, true);
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

    static AgentNavigationGraph.Edge collapseLeadingWalkEdges(List<AgentNavigationGraph.Edge> path) {
        return AgentNavigationPathService.collapseLeadingWalkEdges(path);
    }

    private static boolean isEdgeUsable(AgentNavigationGraph graph, Character bot, AgentNavigationGraph.Edge edge) {
        return AgentNavigationPathService.isEdgeUsable(graph, bot, edge);
    }

    private static boolean sameEdge(AgentNavigationGraph.Edge left, AgentNavigationGraph.Edge right) {
        return AgentNavigationCommittedEdgeService.sameEdge(left, right);
    }

    static boolean shouldRetainCommittedGroundEdge(AgentNavigationGraph.Edge current,
                                                   AgentNavigationGraph.Edge replacement) {
        return AgentNavigationCommittedEdgeService.shouldRetainCommittedGroundEdge(current, replacement);
    }

    static boolean canExecuteJumpFromCurrentPosition(AgentNavigationGraph graph,
                                                     MapleMap map,
                                                     Point botPos,
                                                     AgentNavigationGraph.Edge edge) {
        return AgentNavigationEdgeReadinessService.canExecuteJumpFromCurrentPosition(graph, botPos, edge);
    }

    private static boolean canExecuteSelectedJumpFromCurrentPosition(AgentNavigationGraph graph,
                                                                     BotEntry entry,
                                                                     MapleMap map,
                                                                     Point botPos,
                                                                     AgentNavigationGraph.Edge edge) {
        if (!canExecuteJumpFromCurrentPosition(graph, map, botPos, edge)) {
            return false;
        }
        int launchX = selectedJumpLaunchX(entry, graph, edge);
        int tolerance = Math.max(1, AgentMovementKinematicsService.walkStep(map,
                entry != null ? AgentBotMovementStateRuntime.movementProfile(entry) : null));
        return AgentNavigationEdgeReadinessService.canExecuteSelectedJumpFromCurrentPosition(
                graph, botPos, edge, launchX, tolerance);
    }

    private static boolean isReachableWithinRegion(AgentNavigationGraph graph,
                                                   MapleMap map,
                                                   int regionId,
                                                   Point fromPos,
                                                   Point toPos) {
        AgentNavigationGraph.Region region = graph.getRegion(regionId);
        if (region == null || fromPos == null || toPos == null) {
            return false;
        }
        if (region.isRopeRegion) {
            return fromPos.x == toPos.x;
        }

        int dir = Integer.compare(toPos.x, fromPos.x);
        Point previous = region.pointAt(fromPos.x);
        if (graph.findRegionId(map, previous) != regionId) {
            return false;
        }
        if (dir == 0) {
            return Math.abs(toPos.y - previous.y) <= AgentMovementPhysicsConfig.configuredJumpYThreshold();
        }

        for (int x = fromPos.x + dir; x != toPos.x + dir; x += dir) {
            Point current = region.pointAt(x);
            if (graph.findRegionId(map, current) != regionId) {
                return false;
            }
            if (!AgentNavigationPhysicsService.isWalkableEndpointStep(Math.abs(current.x - previous.x), current.y - previous.y)) {
                return false;
            }
            previous = current;
        }
        return true;
    }

    static boolean isWithinJumpLaunchWindow(AgentNavigationGraph graph,
                                            Point botPos,
                                            AgentNavigationGraph.Edge edge) {
        return AgentNavigationLaunchWindowService.isWithinJumpLaunchWindow(graph, botPos, edge);
    }

    static boolean isWithinDropLaunchWindow(AgentNavigationGraph graph,
                                            Point botPos,
                                            AgentNavigationGraph.Edge edge) {
        return AgentNavigationLaunchWindowService.isWithinDropLaunchWindow(graph, botPos, edge);
    }

    private static int selectedJumpLaunchX(BotEntry entry,
                                           AgentNavigationGraph graph,
                                           AgentNavigationGraph.Edge edge) {
        return AgentNavigationWaypointService.selectJumpLaunchX(entry, graph, edge);
    }

    static boolean shouldUsePreciseWalkTarget(AgentNavigationGraph.Edge edge) {
        return AgentNavigationPathService.shouldUsePreciseWalkTarget(edge);
    }

    private static boolean canExecuteClimbEntryFromCurrentPosition(MapleMap map,
                                                                   Point botPos,
                                                                   AgentNavigationGraph.Edge edge,
                                                                   Rope rope) {
        return AgentNavigationRopeEdgeService.canExecuteClimbEntryFromCurrentPosition(botPos, edge, rope);
    }

    private static boolean canExecuteClimbExitFromCurrentPosition(AgentNavigationGraph graph,
                                                                  MapleMap map,
                                                                  Point botPos,
                                                                  AgentNavigationGraph.Edge edge) {
        return AgentNavigationRopeEdgeService.canExecuteClimbExitFromCurrentPosition(
                graph, botPos, edge, region -> findRopeForRegion(map, region));
    }

    private static Point adjustPathTarget(BotEntry entry,
                                          AgentNavigationGraph graph,
                                          int targetRegionId,
                                          Point rawTargetPos) {
        return AgentNavigationGrindTargetService.adjustPathTarget(
                AgentBotModeStateRuntime.grinding(entry), graph, targetRegionId, rawTargetPos);
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

    private static boolean isRopeEntryEdge(AgentNavigationGraph graph, AgentNavigationGraph.Edge edge) {
        return AgentNavigationRopeEdgeService.isRopeEntryEdge(graph, edge);
    }

    static boolean isTopStepOffExit(Rope rope, Point botPos, AgentNavigationGraph.Edge edge) {
        return AgentNavigationRopeEdgeService.isTopStepOffExit(rope, botPos, edge);
    }

    private static Rope findRopeForRegion(MapleMap map, AgentNavigationGraph.Region region) {
        return AgentNavigationGraphService.findRopeFromRegion(map, region);
    }

}
