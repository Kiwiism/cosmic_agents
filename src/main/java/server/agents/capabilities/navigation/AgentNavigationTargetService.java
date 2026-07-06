package server.agents.capabilities.navigation;

import client.Character;
import server.agents.capabilities.movement.AgentMovementKinematicsService;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotMovementTargetSideEffects;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.runtime.AgentPerformanceMonitor;
import server.agents.runtime.AgentRuntimeEntry;
import server.bots.BotEntry;
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

    public static NavigationDirective resolveTarget(BotEntry entry, Point rawTargetPos, boolean runAiTick) {
        long startedAt = System.nanoTime();
        try {
            Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
            if (bot.getMap().getFootholds() == null) {
                AgentBotNavigationDebugStateRuntime.clearGraphWarmupFallback(entry);
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
                AgentBotNavigationDebugStateRuntime.setGraphWarmupFallback(entry, true);
                AgentMovementStateResetService.clearNavigationState(entry);
                return new NavigationDirective(rawTargetPos, false);
            }

            AgentNavigationGraph graph = resolveActiveGraph(bot.getMap(), AgentBotMovementStateRuntime.movementProfile(entry));
            if (graph == null) {
                AgentNavigationGraphService.warmGraphAsync(bot.getMap(), AgentBotMovementStateRuntime.movementProfile(entry));
                AgentBotNavigationDebugStateRuntime.setGraphWarmupFallback(entry, true);
                AgentNavigationWarmupService.notifyWarmup(entry, bot);
                AgentBotNavigationDebugStateRuntime.setLastDecision(entry, "graph-warmup");
                AgentMovementStateResetService.clearNavigationState(entry);
                Point fallbackTarget = rawTargetPos != null ? new Point(rawTargetPos) : bot.getPosition();
                AgentBotNavigationDebugStateRuntime.recordPathLog(entry,
                        AgentBotMovementTargetSideEffects.captureTargetSnapshot(entry, rawTargetPos),
                        -1, false, runAiTick);
                return new NavigationDirective(fallbackTarget, false);
            }
            if (AgentNavigationGraphService.peekGraph(bot.getMap(), AgentBotMovementStateRuntime.movementProfile(entry)) == null) {
                AgentNavigationGraphService.warmGraphAsync(bot.getMap(), AgentBotMovementStateRuntime.movementProfile(entry));
                AgentBotNavigationDebugStateRuntime.setLastDecision(entry, "graph-fallback-profile");
            }
            AgentBotNavigationDebugStateRuntime.clearGraphWarmupFallback(entry);
            Point botPos = bot.getPosition();
            int startRegionId = AgentNavigationRegionService.resolveCurrentRegionId(graph, entry, bot.getMap(), botPos);
            int targetRegionId = AgentNavigationRegionService.resolveTargetRegionId(graph, entry, bot.getMap(), rawTargetPos);
            Point pathTargetPos = adjustPathTarget(entry, graph, targetRegionId, rawTargetPos);

            AgentNavigationGraph.Edge edge = reuseCommittedEdge(graph, entry, startRegionId, targetRegionId);
            boolean edgeReused = (edge != null);
            if (edgeReused) {
                AgentNavigationGraph.Edge refreshedEdge = refreshPendingClimbExitEdge(
                        graph, entry, bot, botPos, startRegionId, targetRegionId, pathTargetPos, edge, runAiTick);
                if (refreshedEdge != edge) {
                    edge = refreshedEdge;
                    edgeReused = edge != null;
                }
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
                edge = AgentNavigationPathService.findNextEdge(graph, bot, startRegionId, targetRegionId, pathTargetPos);
                if (edge != null) {
                    AgentBotNavigationDebugStateRuntime.setActiveNavigationEdge(entry, edge);
                    AgentBotNavigationDebugStateRuntime.setNavTargetRegionId(entry, targetRegionId);
                }
            }

            if (edge == null) {
                AgentBotNavigationDebugStateRuntime.setLastDecision(entry, !runAiTick ? "no-ai"
                        : startRegionId < 0 || targetRegionId < 0 ? "no-region"
                        : startRegionId == targetRegionId ? "same-region" : "no-path");
                AgentMovementStateResetService.clearNavigationState(entry);
                AgentBotNavigationDebugStateRuntime.recordPathLog(entry,
                        AgentBotMovementTargetSideEffects.captureTargetSnapshot(entry, rawTargetPos),
                        startRegionId, false, runAiTick);
                return new NavigationDirective(rawTargetPos, false);
            }

            NavigationDirective executionDirective = tryExecuteEdge(graph, entry, bot, botPos, rawTargetPos, edge, runAiTick);
            if (executionDirective != null) {
                AgentBotNavigationDebugStateRuntime.setLastDecision(entry, "exec");
                AgentBotNavigationDebugStateRuntime.recordPathLog(entry,
                        AgentBotMovementTargetSideEffects.captureTargetSnapshot(entry, rawTargetPos),
                        startRegionId, true, runAiTick);
                return executionDirective;
            }

            AgentBotNavigationDebugStateRuntime.setLastDecision(entry, edgeReused ? "reuse" : "new");
            AgentBotNavigationDebugStateRuntime.setNavWaypoint(
                    entry,
                    selectWaypoint(entry, graph, botPos, edge),
                    shouldUsePreciseTarget(graph, entry, botPos, edge));
            AgentBotNavigationDebugStateRuntime.recordPathLog(entry,
                    AgentBotMovementTargetSideEffects.captureTargetSnapshot(entry, rawTargetPos),
                    startRegionId, false, runAiTick);
            return new NavigationDirective(AgentBotNavigationDebugStateRuntime.navTargetPosition(entry), false);
        } finally {
            AgentPerformanceMonitor.record("nav-resolve", System.nanoTime() - startedAt);
        }
    }

    public static boolean tryExecuteCommittedEdgeAfterGroundMovement(BotEntry entry, Point rawTargetPos) {
        if (entry == null
                || !AgentBotRuntimeIdentityRuntime.hasBot(entry)
                || !AgentBotNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)
                || AgentBotMovementStateRuntime.inAir(entry)
                || AgentBotClimbStateRuntime.climbing(entry)) {
            return false;
        }

        // Validate the edge is still applicable before attempting execution.
        // tickAirborne may have landed the bot at the destination in this same tick; the navEdge
        // isn't cleared until the next resolveTarget call, so reuseCommittedEdge would correctly
        // discard a DROP/JUMP edge whose toRegionId matches the bot's current region. Without this
        // check, tryExecuteDrop re-fires from the landing platform where there's no lower foothold,
        // sending the bot out of the map.
        AgentNavigationGraph graph = resolveActiveGraph(
                AgentBotRuntimeIdentityRuntime.botMap(entry),
                AgentBotMovementStateRuntime.movementProfile(entry));
        if (graph == null) {
            AgentNavigationGraphService.warmGraphAsync(
                    AgentBotRuntimeIdentityRuntime.botMap(entry),
                    AgentBotMovementStateRuntime.movementProfile(entry));
            return false;
        }
        Point botPos = AgentBotRuntimeIdentityRuntime.bot(entry).getPosition();
        int startRegionId = AgentNavigationRegionService.resolveCurrentRegionId(
                graph, entry, AgentBotRuntimeIdentityRuntime.botMap(entry), botPos);
        AgentNavigationGraph.Edge edge = reuseCommittedEdge(graph, entry, startRegionId,
                AgentBotNavigationDebugStateRuntime.navTargetRegionId(entry));
        if (edge == null) {
            AgentMovementStateResetService.clearNavigationState(entry);
            return false;
        }

        NavigationDirective directive = tryExecuteEdge(
                graph, entry, AgentBotRuntimeIdentityRuntime.bot(entry), botPos, rawTargetPos, edge, true);
        if (directive == null || !directive.consumedTick) {
            return false;
        }

        AgentBotNavigationDebugStateRuntime.setLastDecision(entry, "exec");
        return true;
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
                AgentNavigationPathService::findNextEdge);
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
                startRegionId, targetRegionId, targetPos, edge, runAiTick, AgentNavigationPathService::findNextEdge);
    }

    private static AgentNavigationGraph.Edge reuseCommittedEdge(AgentNavigationGraph graph,
                                                               BotEntry entry,
                                                               int startRegionId,
                                                               int targetRegionId) {
        return AgentNavigationCommittedEdgeService.reuseCommittedEdge(graph, entry, startRegionId, targetRegionId,
                AgentNavigationPathService::isEdgeUsable,
                AgentNavigationRopeEdgeService::isRopeEntryEdge);
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
                                                          AgentRuntimeEntry readinessEntry,
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
                                AgentBotRuntimeIdentityRuntime.botMap(readinessEntry),
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
                                findRopeForRegion(AgentBotRuntimeIdentityRuntime.botMap(readinessEntry),
                                        readinessGraph.getRegion(readinessEdge.toRegionId)));
                    }
                });
    }

    private static Point selectWaypoint(BotEntry entry,
                                        AgentNavigationGraph graph,
                                        Point botPos,
                                        AgentNavigationGraph.Edge edge) {
        return switch (edge.type) {
            case WALK -> new Point(edge.endPoint);
            case CLIMB -> AgentNavigationWaypointService.selectClimbWaypoint(graph, entry, botPos, edge);
            case JUMP -> AgentBotMovementStateRuntime.inAir(entry)
                    ? new Point(edge.endPoint) : selectJumpWaypoint(graph, entry, botPos, edge);
            case DROP -> AgentNavigationWaypointService.selectDropWaypoint(entry, graph, botPos, edge);
            case PORTAL -> AgentBotMovementStateRuntime.inAir(entry)
                    ? new Point(edge.endPoint) : new Point(edge.startPoint);
        };
    }

    private static Point selectJumpWaypoint(AgentNavigationGraph graph,
                                            BotEntry entry,
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
                entry != null ? AgentBotMovementStateRuntime.movementProfile(entry) : null));
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

    private static Point adjustPathTarget(BotEntry entry,
                                          AgentNavigationGraph graph,
                                          int targetRegionId,
                                          Point rawTargetPos) {
        return AgentNavigationGrindTargetService.adjustPathTarget(
                AgentBotModeStateRuntime.grinding(entry), graph, targetRegionId, rawTargetPos);
    }

    private static Rope findRopeForRegion(MapleMap map, AgentNavigationGraph.Region region) {
        return AgentNavigationGraphService.findRopeFromRegion(map, region);
    }
}
