package server.agents.capabilities.navigation;

import client.Character;
import server.agents.catalog.decision.AgentDecisionCatalogRuntime;
import server.agents.capabilities.movement.AgentMovementKinematicsService;
import server.agents.capabilities.movement.AgentMoveTargetStateRuntime;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.capabilities.movement.AgentClimbStateRuntime;
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
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

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
            long nowMs = System.currentTimeMillis();
            AgentNavigationProgressState progressState =
                    entry.capabilityStates().require(AgentNavigationProgressState.STATE_KEY);
            progressState.observe(bot.getMapId(), pathTargetPos, startRegionId, nowMs);
            AgentNavigationGraph.Edge timedOutEdge = runAiTick
                    ? AgentNavigationEdgeReliabilityRuntime.observeAttempt(
                            entry, bot.getMapId(), startRegionId, botPos, nowMs)
                    : null;
            if (timedOutEdge != null) {
                // Invalidate only the live step. The local pathTarget is replanned below while
                // combat target, objective and unrelated capability state stay intact.
                AgentMovementStateResetService.clearNavigationStep(entry);
                AgentNavigationDebugStateRuntime.setLastDecision(entry, "edge-timeout-replan");
            }
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
            AgentNavigationGraph.Edge previouslyCommittedEdge =
                    (AgentNavigationGraph.Edge) AgentNavigationDebugStateRuntime.activeNavigationEdge(entry);
            if (traversal != null) {
                // A committed rope/ladder traversal owns movement until it settles. Generic
                // ground-cycle recovery must not cancel or blacklist one of its structural edges.
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
                // Search-time overlays are not enough: a previously committed edge can survive
                // into this tick without running A* again. Reject it here when an authored route
                // redirects the current branch.
                if (edge != null && ((!AgentNavigationRouteOverlayPolicy.allows(graph, targetRegionId, edge))
                        || AgentNavigationEdgeReliabilityRuntime.suppressed(
                                entry, bot.getMapId(), edge, nowMs)
                        || AgentVerticalTraversalService.blocksRecentInverseEntry(
                                graph, entry, edge, nowMs))) {
                    AgentNavigationDebugStateRuntime.clearActiveNavigationEdge(entry);
                    AgentNavigationDebugStateRuntime.clearNavJumpLaunch(entry);
                    edge = null;
                    edgeReused = false;
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
                Point verticalExitNudge = AgentVerticalTraversalService.recentExitNudgeTarget(
                        graph, entry, startRegionId, nowMs);
                if (verticalExitNudge != null) {
                    AgentNavigationDebugStateRuntime.clearActiveNavigationEdge(entry);
                    AgentNavigationDebugStateRuntime.clearNavJumpLaunch(entry);
                    AgentNavigationDebugStateRuntime.setNavWaypoint(entry, verticalExitNudge, true);
                    AgentNavigationDebugStateRuntime.setLastDecision(entry, "vertical-exit-nudge");
                    return new NavigationDirective(verticalExitNudge, false);
                }
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
                if (!runAiTick && rawTargetPos != null
                        && AgentClimbStateRuntime.climbing(entry)
                        && previouslyCommittedEdge != null
                        && AgentNavigationRopeEdgeService.isRopeEntryEdge(
                                graph, previouslyCommittedEdge)) {
                    // Observation-only resolution must discard the stale entry edge without
                    // replacing its caller-owned target by the one-pixel rope attachment pose.
                    return new NavigationDirective(new Point(rawTargetPos), false);
                }
                return new NavigationDirective(
                        safeFallbackTarget(
                                botPos,
                                rawTargetPos,
                                startRegionId,
                                targetRegionId,
                                AgentClimbStateRuntime.climbing(entry)),
                        false);
            }

            AgentTraversalResult execution = tryExecuteEdge(
                    graph, entry, bot, botPos, rawTargetPos, startRegionId, edge, runAiTick);
            if (execution.rejected()) {
                AgentMovementStateResetService.clearNavigationStep(entry);
                AgentNavigationDebugStateRuntime.setLastDecision(entry, "edge-rejected-replan");
                if (startRegionId >= 0 && targetRegionId >= 0
                        && AgentNavigationEdgeReliabilityRuntime.suppressed(
                                entry, bot.getMapId(), edge, System.currentTimeMillis())) {
                    AgentNavigationGraph.Edge alternative = findNextEdge(
                            graph, entry, bot, startRegionId, targetRegionId, pathTargetPos);
                    if (alternative != null) {
                        Point replanTargetPos = pathTargetPos;
                        int replanTargetRegionId = targetRegionId;
                        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(entry, alternative);
                        AgentNavigationDebugStateRuntime.setPlannedNavigationTargetPosition(
                                entry, replanTargetPos);
                        AgentNavigationDebugStateRuntime.setNavTargetRegionId(
                                entry, replanTargetRegionId);
                        AgentVerticalTraversalService.beginIfRopeEntry(
                                graph, entry, bot, alternative,
                                replanTargetRegionId, replanTargetPos,
                                (activeGraph, activeBot, activeStartPosition, activeStartRegionId,
                                 activeTargetRegionId, activeTargetPos) -> findNextEdge(
                                        activeGraph, entry, activeBot, activeStartPosition,
                                        activeStartRegionId, activeTargetRegionId, activeTargetPos));
                        AgentNavigationDebugStateRuntime.setNavWaypoint(
                                entry, selectWaypoint(entry, graph, botPos, alternative),
                                shouldUsePreciseTarget(graph, entry, botPos, alternative));
                        return new NavigationDirective(
                                AgentNavigationDebugStateRuntime.navTargetPosition(entry), false);
                    }
                }
                return new NavigationDirective(new Point(botPos), false);
            }
            if (execution.executed()) {
                AgentNavigationDebugStateRuntime.setLastDecision(entry, "exec");
                return new NavigationDirective(
                        execution.targetPosition(), execution.consumedTick());
            }

            AgentNavigationDebugStateRuntime.setLastDecision(entry, edgeReused ? "reuse" : "new");
            AgentNavigationDebugStateRuntime.setNavWaypoint(
                    entry,
                    selectWaypoint(entry, graph, botPos, edge),
                    shouldUsePreciseTarget(graph, entry, botPos, edge));
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

        AgentTraversalResult execution = tryExecuteEdge(
                graph, entry, AgentRuntimeIdentityRuntime.bot(entry), botPos,
                rawTargetPos, startRegionId, edge, true);
        if (execution.rejected()) {
            AgentMovementStateResetService.clearNavigationStep(entry);
            return false;
        }
        if (!execution.executed() || !execution.consumedTick()) {
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
        long nowMs = System.currentTimeMillis();
        boolean authoredRouteOverlay = AgentNavigationRouteOverlayPolicy.applies(graph, targetRegionId);
        Predicate<AgentNavigationGraph.Edge> reliabilityFilter =
                AgentNavigationEdgeReliabilityRuntime.edgeFilter(entry, graph.mapId, nowMs);
        Predicate<AgentNavigationGraph.Edge> edgeFilter = edge ->
                reliabilityFilter.test(edge)
                        && !AgentVerticalTraversalService.blocksRecentInverseEntry(
                                graph, entry, edge, nowMs);
        ToIntFunction<AgentNavigationGraph.Edge> edgePenalty =
                AgentNavigationEdgeReliabilityRuntime.edgePenalty(entry, graph.mapId, nowMs);
        AgentNavigationPathService.MovementPathSelection selection =
                AgentNavigationPathService.findNextEdgeSelectionVaried(
                graph, bot, startPosition, startRegionId, targetRegionId, targetPos, variation,
                edgeFilter, edgePenalty);
        AgentNavigationGraph.Edge selected = selection.activeEdge();
        String routeSource = selection.recoverySearch() ? "RECOVERY_SEARCH"
                : authoredRouteOverlay
                ? "MAP_OVERLAY"
                : variation != null ? "VARIED" : "NORMAL";
        String routeReason = authoredRouteOverlay
                ? AgentNavigationRouteOverlayPolicy.rationale(graph, targetRegionId)
                : selection.recoverySearch() ? "bounded search required recovery budget" : "";
        boolean leavesResolvedTargetRegion = leavesResolvedTargetRegion(
                selected, startRegionId, targetRegionId);
        if (leavesResolvedTargetRegion) {
            selected = null;
            AgentNavigationTraceRuntime.rejected(entry, graph, startRegionId,
                    targetRegionId, targetPos, "EDGE_REJECTED",
                    "candidate leaves resolved target region", nowMs);
        }
        if (!leavesResolvedTargetRegion) {
            if (selected == null && !selection.outcome().reached()) {
                routeSource = "NO_PATH";
                if (routeReason.isBlank()) {
                    routeReason = selection.outcome().capped()
                            ? "search capped before reaching target"
                            : "no usable route to target";
                }
            }
            AgentNavigationTraceRuntime.planned(entry, graph, startRegionId,
                    targetRegionId, targetPos, selection, routeSource, routeReason, nowMs);
        }
        AgentMovementSkillShadowDiagnostics.compare(graph, entry, bot, startPosition,
                startRegionId, targetRegionId, targetPos, selected);
        return selected;
    }

    static Point safeFallbackTarget(Point botPos,
                                    Point rawTargetPos,
                                    int startRegionId,
                                    int targetRegionId) {
        return safeFallbackTarget(botPos, rawTargetPos, startRegionId, targetRegionId, false);
    }

    static Point safeFallbackTarget(Point botPos,
                                    Point rawTargetPos,
                                    int startRegionId,
                                    int targetRegionId,
                                    boolean climbing) {
        // Once attached to a rope or ladder, holding the current position during a transient
        // no-edge replan removes the vertical intent and can strand the Agent indefinitely.
        // The caller-owned destination remains the safe mechanical input here: climbing physics
        // constrains motion to the attached climbable and can dismount normally at its boundary.
        if (climbing && rawTargetPos != null) {
            return new Point(rawTargetPos);
        }
        boolean sameResolvedRegion = startRegionId >= 0 && startRegionId == targetRegionId;
        return sameResolvedRegion && rawTargetPos != null
                ? new Point(rawTargetPos)
                : new Point(botPos);
    }

    static boolean leavesResolvedTargetRegion(AgentNavigationGraph.Edge edge,
                                               int startRegionId,
                                               int targetRegionId) {
        return edge != null
                && startRegionId >= 0
                && startRegionId == targetRegionId
                && edge.fromRegionId == startRegionId
                && edge.toRegionId != startRegionId;
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

    private static AgentTraversalResult tryExecuteEdge(AgentNavigationGraph graph,
                                                AgentRuntimeEntry entry,
                                                Character bot,
                                                Point botPos,
                                                Point rawTargetPos,
                                                int currentRegionId,
                                                AgentNavigationGraph.Edge edge,
                                                boolean runAiTick) {
        if (!runAiTick) {
            return AgentTraversalResult.deferred("observation-only");
        }
        long nowMs = System.currentTimeMillis();
        // Observe the complete committed-edge attempt, including the approach to its launch or
        // attachment point. Validation can remain DEFERRED forever when a bad approach repeatedly
        // sends the Agent through an adjacent region, so starting only after READY made that class
        // of A/B oscillation invisible to edge reliability. Monotonic destination progress keeps
        // legitimate approaches and long climbs alive while bounded non-progress is rejected.
        AgentNavigationEdgeReliabilityRuntime.beganAttempt(
                entry, bot.getMapId(), edge, currentRegionId, botPos, nowMs);
        AgentNavigationEdgeValidationService.Result validation =
                AgentNavigationEdgeValidationService.validate(
                        graph, entry, bot, bot.getMapId(), currentRegionId, botPos, edge, nowMs);
        if (validation.rejected()) {
            if (!"edge-suppressed".equals(validation.reason())) {
                AgentNavigationEdgeReliabilityRuntime.failed(
                        entry, bot.getMapId(), edge, nowMs);
            }
            return AgentTraversalResult.rejected(validation.reason());
        }
        if (!validation.ready()) {
            return AgentTraversalResult.deferred(validation.reason());
        }
        return AgentNavigationEdgeExecutor.INSTANCE.execute(
                entry, bot, new AgentTraversalCommand(graph, edge, botPos, rawTargetPos));
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
            if ((edge.type == AgentNavigationGraph.EdgeType.JUMP
                    || edge.type == AgentNavigationGraph.EdgeType.FLASH_JUMP)
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
            case JUMP, FLASH_JUMP -> AgentMovementStateRuntime.inAir(entry)
                    ? new Point(edge.endPoint) : selectJumpWaypoint(graph, entry, botPos, edge);
            case TELEPORT -> new Point(edge.startPoint);
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
