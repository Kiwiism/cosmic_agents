package server.agents.capabilities.navigation;

import client.Character;
import server.agents.catalog.decision.AgentDecisionCatalogRuntime;
import server.agents.capabilities.movement.AgentMovementKinematicsService;
import server.agents.capabilities.movement.AgentMoveTargetStateRuntime;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.capabilities.movement.AgentClimbStateRuntime;
import server.agents.capabilities.movement.AgentMovementTargetRuntime;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.monitoring.AgentPerformanceMonitor;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.events.AgentEventPriority;
import server.agents.operations.events.AgentNavigationRouteFailedEvent;
import server.agents.operations.events.AgentOperationalEventPublisher;
import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.Point;

/**
 * Agent-owned live navigation target resolver.
 */
public final class AgentNavigationTargetService {
    private AgentNavigationTargetService() {
    }

    public record NavigationDirective(Point targetPos, boolean consumedTick) {
    }

    public static NavigationDirective resolveTarget(AgentRuntimeEntry entry, Point rawTargetPos, boolean runAiTick) {
        long startedAt = System.nanoTime();
        try {
            Character bot = AgentRuntimeIdentityRuntime.bot(entry);
            if (bot.getMap().getFootholds() == null) {
                AgentNavigationDebugStateRuntime.clearGraphWarmupFallback(entry);
                AgentMovementStateResetService.clearNavigationState(entry);
                return new NavigationDirective(rawTargetPos, false);
            }
            if (bot.getMap().isSwim()) {
                // Swim maps don't use a swim-aware nav graph. Airborne motion is handled
                // by the swim integrator (tickSwimming); on platforms we still need
                // ledge-drops, ropes, and ground jumps. Engage the heuristic fallback -
                // it walks off ledges into water, picks up nearby ropes, and jumps onto
                // higher platforms when useful. tickSwimming consults targetPos directly,
                // so the same rawTargetPos works for both grounded and airborne paths.
                AgentNavigationDebugStateRuntime.setGraphWarmupFallback(entry, true);
                AgentMovementStateResetService.clearNavigationState(entry);
                return new NavigationDirective(rawTargetPos, false);
            }

            AgentNavigationGraph graph = resolveActiveGraph(bot.getMap(), AgentMovementStateRuntime.movementProfile(entry));
            if (graph == null) {
                AgentNavigationGraphService.warmGraphAsync(
                        entry, bot.getMap(), AgentMovementStateRuntime.movementProfile(entry));
                AgentNavigationDebugStateRuntime.setGraphWarmupFallback(entry, true);
                AgentNavigationWarmupService.notifyWarmup(entry, bot);
                AgentNavigationDebugStateRuntime.setLastDecision(entry, "graph-warmup");
                AgentMovementStateResetService.clearNavigationState(entry);
                // A raw cross-map target is not a safe warm-up fallback. In disconnected maps
                // (notably Lith Harbor's ship arrival) steering toward that coordinate makes an
                // airborne Agent travel through open space before the graph exposes the hidden
                // transfer portal. Hold horizontal position and let ordinary gravity settle the
                // Agent; the next AI pass retries once the graph is available.
                Point fallbackTarget = bot.getPosition();
                AgentNavigationDebugStateRuntime.recordPathLog(entry,
                        AgentMovementTargetRuntime.captureTargetSnapshot(entry, rawTargetPos),
                        -1, false, runAiTick);
                return new NavigationDirective(fallbackTarget, false);
            }
            if (AgentNavigationGraphService.peekGraph(bot.getMap(), AgentMovementStateRuntime.movementProfile(entry)) == null) {
                AgentNavigationGraphService.warmGraphAsync(
                        entry, bot.getMap(), AgentMovementStateRuntime.movementProfile(entry));
                AgentNavigationDebugStateRuntime.setLastDecision(entry, "graph-fallback-profile");
            }
            AgentNavigationDebugStateRuntime.clearGraphWarmupFallback(entry);
            Point botPos = bot.getPosition();
            int startRegionId = AgentNavigationRegionService.resolveCurrentRegionId(graph, entry, bot.getMap(), botPos);
            int targetRegionId = AgentNavigationRegionService.resolveTargetRegionId(graph, entry, bot.getMap(), rawTargetPos);
            Point pathTargetPos = adjustPathTarget(entry, graph, targetRegionId, rawTargetPos);
            if (runAiTick && rawTargetPos != null) {
                AgentDecisionCatalogRuntime.observeNavigation(
                        entry,
                        bot.getMapId(),
                        botPos.x,
                        botPos.y,
                        rawTargetPos.x,
                        rawTargetPos.y,
                        startRegionId,
                        targetRegionId,
                        System.currentTimeMillis());
            }

            boolean traversalWasActive = AgentVerticalTraversalStateRuntime.active(entry);
            AgentVerticalTraversalService.TraversalDirective traversal =
                    AgentVerticalTraversalService.resolve(
                            graph, entry, bot, startRegionId, runAiTick,
                            AgentNavigationPathService::isEdgeUsable);
            if (traversal != null && traversal.holdGroundedExit()) {
                AgentNavigationDebugStateRuntime.clearActiveNavigationEdge(entry);
                AgentNavigationDebugStateRuntime.clearNavJumpLaunch(entry);
                AgentNavigationDebugStateRuntime.setNavTargetRegionId(entry, traversal.targetRegionId());
                AgentNavigationDebugStateRuntime.setNavWaypoint(entry, traversal.targetPosition(), false);
                AgentNavigationDebugStateRuntime.setLastDecision(entry, "vertical-settle");
                AgentNavigationDebugStateRuntime.recordPathLog(entry,
                        AgentMovementTargetRuntime.captureTargetSnapshot(entry, rawTargetPos),
                        startRegionId, false, runAiTick);
                return new NavigationDirective(traversal.targetPosition(), false);
            }
            if (traversalWasActive && traversal == null) {
                // The transaction either completed or invalidated. Do not let its last component
                // leak into ordinary live-target planning.
                AgentNavigationDebugStateRuntime.clearActiveNavigationEdge(entry);
                AgentNavigationDebugStateRuntime.clearNavJumpLaunch(entry);
                AgentNavigationDebugStateRuntime.clearNavTarget(entry);
            }

            AgentNavigationGraph.Edge edge;
            boolean edgeReused;
            if (traversal != null) {
                edge = traversal.edge();
                pathTargetPos = traversal.targetPosition();
                targetRegionId = traversal.targetRegionId();
                edgeReused = true;
                AgentNavigationDebugStateRuntime.setActiveNavigationEdge(entry, edge);
                AgentNavigationDebugStateRuntime.setPlannedNavigationTargetPosition(entry, pathTargetPos);
                AgentNavigationDebugStateRuntime.setNavTargetRegionId(entry, targetRegionId);
            } else {
                edge = reuseCommittedEdge(graph, entry, startRegionId, targetRegionId, pathTargetPos);
                edgeReused = edge != null;
                if (edgeReused) {
                    AgentNavigationGraph.Edge refreshedGroundEdge = refreshCommittedGroundEdge(
                            graph, entry, bot, startRegionId, targetRegionId, pathTargetPos, edge, runAiTick);
                    if (refreshedGroundEdge != edge) {
                        edge = refreshedGroundEdge;
                        edgeReused = edge != null;
                    }
                }
            }
            if (edge == null && runAiTick && startRegionId >= 0 && targetRegionId >= 0) {
                // Same-region planning is intentionally allowed: intra-region portals appear as
                // self-loop edges (fromRegionId == toRegionId) and A* picks them when the
                // walk-to-entry + walk-from-exit cost beats the direct walk. findPath returns
                // an empty path when direct walk wins, falling through to direct steering.
                edge = findNextEdge(graph, entry, bot, startRegionId, targetRegionId, pathTargetPos);
                if (edge != null) {
                    AgentNavigationDebugStateRuntime.setActiveNavigationEdge(entry, edge);
                    AgentNavigationDebugStateRuntime.setPlannedNavigationTargetPosition(entry, pathTargetPos);
                    AgentNavigationDebugStateRuntime.setNavTargetRegionId(entry, targetRegionId);
                    AgentVerticalTraversalService.beginIfRopeEntry(
                            graph, entry, bot, edge, targetRegionId, pathTargetPos,
                            (activeGraph, activeBot, activeStartPosition, activeStartRegionId,
                             activeTargetRegionId, activeTargetPos) ->
                                    findNextEdge(activeGraph, entry, activeBot, activeStartPosition,
                                            activeStartRegionId, activeTargetRegionId, activeTargetPos));
                }
            }

            if (edge == null) {
                String previousDecision = AgentNavigationDebugStateRuntime.lastDecision(entry);
                String decision = !runAiTick ? "no-ai"
                        : startRegionId < 0 || targetRegionId < 0 ? "no-region"
                        : startRegionId == targetRegionId ? "same-region" : "no-path";
                AgentNavigationDebugStateRuntime.setLastDecision(entry, decision);
                if ("no-path".equals(decision) && !decision.equals(previousDecision)) {
                    Point failedTarget = pathTargetPos == null ? botPos : pathTargetPos;
                    int failedStartRegionId = startRegionId;
                    int failedTargetRegionId = targetRegionId;
                    AgentOperationalEventPublisher.publish(entry,
                            objectiveId -> new AgentNavigationRouteFailedEvent(
                                    bot.getId(), System.currentTimeMillis(), bot.getMapId(),
                                    failedStartRegionId, failedTargetRegionId,
                                    failedTarget.x, failedTarget.y,
                                    decision, objectiveId),
                            AgentEventPriority.IMPORTANT);
                }
                AgentMovementStateResetService.clearNavigationState(entry);
                AgentNavigationDebugStateRuntime.recordPathLog(entry,
                        AgentMovementTargetRuntime.captureTargetSnapshot(entry, rawTargetPos),
                        startRegionId, false, runAiTick);
                return new NavigationDirective(
                        safeFallbackTarget(botPos, rawTargetPos, startRegionId, targetRegionId),
                        false);
            }

            NavigationDirective executionDirective = tryExecuteEdge(graph, entry, bot, botPos, rawTargetPos, edge, runAiTick);
            if (executionDirective != null) {
                AgentNavigationDebugStateRuntime.setLastDecision(entry, "exec");
                AgentNavigationDebugStateRuntime.recordPathLog(entry,
                        AgentMovementTargetRuntime.captureTargetSnapshot(entry, rawTargetPos),
                        startRegionId, true, runAiTick);
                return executionDirective;
            }

            AgentNavigationDebugStateRuntime.setLastDecision(entry, edgeReused ? "reuse" : "new");
            AgentNavigationDebugStateRuntime.setNavWaypoint(
                    entry,
                    selectWaypoint(entry, graph, botPos, edge),
                    shouldUsePreciseTarget(graph, entry, botPos, edge));
            AgentNavigationDebugStateRuntime.recordPathLog(entry,
                    AgentMovementTargetRuntime.captureTargetSnapshot(entry, rawTargetPos),
                    startRegionId, false, runAiTick);
            return new NavigationDirective(AgentNavigationDebugStateRuntime.navTargetPosition(entry), false);
        } finally {
            AgentPerformanceMonitor.record("nav-resolve", System.nanoTime() - startedAt);
        }
    }

    public static boolean tryExecuteCommittedEdgeAfterGroundMovement(AgentRuntimeEntry entry, Point rawTargetPos) {
        if (entry == null
                || !AgentRuntimeIdentityRuntime.hasBot(entry)
                || !AgentNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)
                || AgentMovementStateRuntime.inAir(entry)
                || AgentClimbStateRuntime.climbing(entry)) {
            return false;
        }

        // Validate the edge is still applicable before attempting execution.
        // tickAirborne may have landed the bot at the destination in this same tick; the navEdge
        // isn't cleared until the next resolveTarget call, so reuseCommittedEdge would correctly
        // discard a DROP/JUMP edge whose toRegionId matches the bot's current region. Without this
        // check, tryExecuteDrop re-fires from the landing platform where there's no lower foothold,
        // sending the bot out of the map.
        AgentNavigationGraph graph = resolveActiveGraph(
                AgentRuntimeIdentityRuntime.botMap(entry),
                AgentMovementStateRuntime.movementProfile(entry));
        if (graph == null) {
            AgentNavigationGraphService.warmGraphAsync(
                    entry,
                    AgentRuntimeIdentityRuntime.botMap(entry),
                    AgentMovementStateRuntime.movementProfile(entry));
            return false;
        }
        Point botPos = AgentRuntimeIdentityRuntime.bot(entry).getPosition();
        int startRegionId = AgentNavigationRegionService.resolveCurrentRegionId(
                graph, entry, AgentRuntimeIdentityRuntime.botMap(entry), botPos);
        AgentNavigationGraph.Edge edge = reuseCommittedEdge(graph, entry, startRegionId,
                AgentNavigationDebugStateRuntime.navTargetRegionId(entry),
                AgentNavigationDebugStateRuntime.plannedNavigationTargetPosition(entry));
        if (edge == null) {
            if (AgentVerticalTraversalStateRuntime.active(entry)) {
                // Landing can complete the exit edge between resolveTarget and this post-movement
                // hook. Preserve the wider traversal transaction so the next resolver observes
                // the grounded destination and performs its one-tick hand-off there.
                AgentNavigationDebugStateRuntime.clearActiveNavigationEdge(entry);
                AgentNavigationDebugStateRuntime.clearNavJumpLaunch(entry);
            } else {
                AgentMovementStateResetService.clearNavigationState(entry);
            }
            return false;
        }

        NavigationDirective directive = tryExecuteEdge(
                graph, entry, AgentRuntimeIdentityRuntime.bot(entry), botPos, rawTargetPos, edge, true);
        if (directive == null || !directive.consumedTick) {
            return false;
        }

        AgentNavigationDebugStateRuntime.setLastDecision(entry, "exec");
        return true;
    }

    private static AgentNavigationGraph.Edge refreshCommittedGroundEdge(AgentNavigationGraph graph,
                                                                        AgentRuntimeEntry entry,
                                                                        Character bot,
                                                                        int startRegionId,
                                                                        int targetRegionId,
                                                                        Point targetPos,
                                                                        AgentNavigationGraph.Edge edge,
                                                                        boolean runAiTick) {
        return AgentNavigationCommittedEdgeService.refreshCommittedGroundEdge(graph, entry, bot,
                startRegionId, targetRegionId, targetPos, edge, runAiTick,
                (activeGraph, activeBot, activeStartRegionId, activeTargetRegionId, activeTargetPos) ->
                        findNextEdge(activeGraph, entry, activeBot, activeStartRegionId,
                                activeTargetRegionId, activeTargetPos));
    }

    private static AgentNavigationGraph.Edge reuseCommittedEdge(AgentNavigationGraph graph,
                                                               AgentRuntimeEntry entry,
                                                               int startRegionId,
                                                               int targetRegionId,
                                                               Point targetPos) {
        return AgentNavigationCommittedEdgeService.reuseCommittedEdge(graph, entry, startRegionId, targetRegionId,
                targetPos,
                AgentNavigationPathService::isEdgeUsable,
                AgentNavigationRopeEdgeService::isRopeEntryEdge);
    }

    private static AgentNavigationGraph.Edge findNextEdge(AgentNavigationGraph graph,
                                                           AgentRuntimeEntry entry,
                                                           Character bot,
                                                           int startRegionId,
                                                           int targetRegionId,
                                                           Point targetPos) {
        return findNextEdge(graph, entry, bot, bot.getPosition(), startRegionId, targetRegionId, targetPos);
    }

    private static AgentNavigationGraph.Edge findNextEdge(AgentNavigationGraph graph,
                                                           AgentRuntimeEntry entry,
                                                           Character bot,
                                                           Point startPosition,
                                                           int startRegionId,
                                                           int targetRegionId,
                                                           Point targetPos) {
        AgentTravelVariationRuntime.RouteVariation variation = scriptedRouteVariation(
                entry, graph.mapId, targetRegionId, targetPos);
        return AgentNavigationPathService.findNextEdgeVaried(
                graph, bot.getMap(), startPosition, startRegionId, targetRegionId, targetPos, variation);
    }

    static Point safeFallbackTarget(Point botPos,
                                    Point rawTargetPos,
                                    int startRegionId,
                                    int targetRegionId) {
        boolean sameResolvedRegion = startRegionId >= 0 && startRegionId == targetRegionId;
        return sameResolvedRegion && rawTargetPos != null
                ? new Point(rawTargetPos)
                : new Point(botPos);
    }

    static AgentTravelVariationRuntime.RouteVariation scriptedRouteVariation(
            AgentRuntimeEntry entry,
            int mapId,
            int targetRegionId,
            Point targetPos) {
        Point scriptedTarget = AgentMoveTargetStateRuntime.moveTarget(entry);
        return scriptedTarget != null && scriptedTarget.equals(targetPos)
                ? AgentTravelVariationRuntime.routeVariation(
                entry, mapId, targetRegionId, scriptedTarget)
                : null;
    }

    private static NavigationDirective tryExecuteEdge(AgentNavigationGraph graph,
                                                      AgentRuntimeEntry entry,
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

    private static boolean shouldUsePreciseTarget(AgentNavigationGraph graph,
                                                  AgentRuntimeEntry entry,
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
                                                          AgentRuntimeEntry readinessEntry,
                                                          Point readinessBotPos,
                                                          AgentNavigationGraph.Edge readinessEdge) {
                        return canExecuteSelectedJumpFromCurrentPosition(
                                readinessGraph,
                                readinessEntry,
                                AgentRuntimeIdentityRuntime.botMap(readinessEntry),
                                readinessBotPos,
                                readinessEdge);
                    }

                    @Override
                    public boolean canExecuteDrop(AgentNavigationGraph readinessGraph,
                                                  AgentRuntimeEntry readinessEntry,
                                                  Point readinessBotPos,
                                                  AgentNavigationGraph.Edge readinessEdge) {
                        return AgentNavigationEdgeReadinessService.canExecuteDropFromCurrentPosition(
                                readinessGraph,
                                readinessBotPos,
                                readinessEdge);
                    }

                    @Override
                    public boolean canExecuteClimbExit(AgentNavigationGraph readinessGraph,
                                                       AgentRuntimeEntry readinessEntry,
                                                       Point readinessBotPos,
                                                       AgentNavigationGraph.Edge readinessEdge) {
                        return canExecuteClimbExitFromCurrentPosition(
                                readinessGraph,
                                AgentRuntimeIdentityRuntime.botMap(readinessEntry),
                                readinessBotPos,
                                readinessEdge);
                    }

                    @Override
                    public boolean canExecuteClimbEntry(AgentNavigationGraph readinessGraph,
                                                        AgentRuntimeEntry readinessEntry,
                                                        Point readinessBotPos,
                                                        AgentNavigationGraph.Edge readinessEdge) {
                        return AgentNavigationRopeEdgeService.canExecuteClimbEntryFromCurrentPosition(
                                readinessBotPos,
                                readinessEdge,
                                findRopeForRegion(AgentRuntimeIdentityRuntime.botMap(readinessEntry),
                                        readinessGraph.getRegion(readinessEdge.toRegionId)));
                    }
                });
    }

    private static Point selectWaypoint(AgentRuntimeEntry entry,
                                        AgentNavigationGraph graph,
                                        Point botPos,
                                        AgentNavigationGraph.Edge edge) {
        if (!AgentMovementStateRuntime.inAir(entry) && !AgentClimbStateRuntime.climbing(entry)) {
            if (edge.type == AgentNavigationGraph.EdgeType.JUMP
                    || edge.type == AgentNavigationGraph.EdgeType.CLIMB
                    || edge.type == AgentNavigationGraph.EdgeType.DROP) {
                Point detour = AgentFootholdDetourService.waypoint(entry, graph, botPos, edge);
                if (detour != null) {
                    return detour;
                }
            } else {
                AgentFootholdDetourService.clear(entry);
            }
        } else {
            AgentFootholdDetourService.clear(entry);
        }
        return switch (edge.type) {
            case WALK -> new Point(edge.endPoint);
            case CLIMB -> AgentNavigationWaypointService.selectClimbWaypoint(graph, entry, botPos, edge);
            case JUMP -> AgentMovementStateRuntime.inAir(entry)
                    ? new Point(edge.endPoint) : selectJumpWaypoint(graph, entry, botPos, edge);
            case DROP -> AgentNavigationWaypointService.selectDropWaypoint(entry, graph, botPos, edge);
            case PORTAL -> AgentMovementStateRuntime.inAir(entry)
                    ? new Point(edge.endPoint) : new Point(edge.startPoint);
        };
    }

    private static Point selectJumpWaypoint(AgentNavigationGraph graph,
                                            AgentRuntimeEntry entry,
                                            Point botPos,
                                            AgentNavigationGraph.Edge edge) {
        AgentNavigationGraph.Region fromRegion = graph.getRegion(edge.fromRegionId);
        if (fromRegion == null || fromRegion.isRopeRegion) {
            return new Point(edge.startPoint);
        }
        int targetX = AgentNavigationWaypointService.selectJumpLaunchX(entry, graph, edge);
        return fromRegion.pointAt(targetX);
    }

    private static AgentNavigationGraph resolveActiveGraph(MapleMap map,
                                                           server.agents.capabilities.movement.AgentMovementProfile movementProfile) {
        return AgentNavigationGraphService.peekBestGraph(map, movementProfile);
    }

    private static boolean canExecuteSelectedJumpFromCurrentPosition(AgentNavigationGraph graph,
                                                                     AgentRuntimeEntry entry,
                                                                     MapleMap map,
                                                                     Point botPos,
                                                                     AgentNavigationGraph.Edge edge) {
        if (!AgentNavigationEdgeReadinessService.canExecuteJumpFromCurrentPosition(graph, botPos, edge)) {
            return false;
        }
        int launchX = AgentNavigationWaypointService.selectJumpLaunchX(entry, graph, edge);
        int tolerance = Math.max(1, AgentMovementKinematicsService.walkStep(map,
                entry != null ? AgentMovementStateRuntime.movementProfile(entry) : null));
        return AgentNavigationEdgeReadinessService.canExecuteSelectedJumpFromCurrentPosition(
                graph, botPos, edge, launchX, tolerance);
    }

    private static boolean canExecuteClimbExitFromCurrentPosition(AgentNavigationGraph graph,
                                                                  MapleMap map,
                                                                  Point botPos,
                                                                  AgentNavigationGraph.Edge edge) {
        return AgentNavigationRopeEdgeService.canExecuteClimbExitFromCurrentPosition(
                graph, botPos, edge, region -> findRopeForRegion(map, region));
    }

    private static Point adjustPathTarget(AgentRuntimeEntry entry,
                                          AgentNavigationGraph graph,
                                          int targetRegionId,
                                          Point rawTargetPos) {
        return AgentNavigationGrindTargetService.adjustPathTarget(
                AgentModeStateRuntime.grinding(entry), graph, targetRegionId, rawTargetPos);
    }

    private static Rope findRopeForRegion(MapleMap map, AgentNavigationGraph.Region region) {
        return AgentNavigationGraphService.findRopeFromRegion(map, region);
    }
}
