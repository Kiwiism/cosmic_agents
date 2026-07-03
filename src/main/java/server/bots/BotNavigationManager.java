package server.bots;

import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.navigation.AgentNavigationCommittedEdgeService;
import server.agents.capabilities.navigation.AgentNavigationEdgeReadinessService;
import server.agents.capabilities.navigation.AgentNavigationGrindTargetService;
import server.agents.capabilities.navigation.AgentNavigationLaunchWindowService;
import server.agents.capabilities.navigation.AgentNavigationPhysicsService;
import server.agents.capabilities.navigation.AgentNavigationPathService;
import server.agents.capabilities.navigation.AgentNavigationRegionService;
import server.agents.capabilities.navigation.AgentNavigationRopeEdgeService;
import server.agents.capabilities.navigation.AgentNavigationWaypointService;

import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.runtime.AgentPerformanceMonitor;

import server.agents.capabilities.movement.AgentClimbMovementPolicy;
import server.agents.capabilities.movement.AgentClimbMovementService;
import server.agents.capabilities.movement.AgentGroundCollisionService;
import server.agents.capabilities.movement.AgentJumpActionService;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementKinematicsService;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.capabilities.movement.AgentQueuedMovementActionService;
import server.agents.capabilities.movement.AgentRopeMovementService;

import client.Character;
import constants.game.CharacterStance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.movement.AgentMovementTargetSnapshot;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotFarmAnchorStateRuntime;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.agents.integration.AgentBotMovementTargetSideEffects;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.integration.AgentBotSessionLifecycleSideEffects;
import server.agents.integration.AgentBotShopStateRuntime;
import server.agents.runtime.AgentFollowAnchorService;
import server.maps.MapleMap;
import server.maps.Foothold;
import server.maps.Portal;
import server.maps.Rope;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

public final class BotNavigationManager {
    private static final Logger log = LoggerFactory.getLogger(BotNavigationManager.class);
    // After a bot takes a portal, suppress further portal usage for this long. Prevents a bot from
    // immediately re-entering a portal (e.g. bouncing back through the return portal). Gates ONLY
    // portal execution — movement, attacks and every other action continue unaffected.
    private static final long PORTAL_USE_COOLDOWN_MS = 250L;
    private static final long SLOW_PATHFIND_WARN_NS = 50_000_000L;

    /** Throttle warmup notifications per (ownerId -> mapId -> lastNotifyMs). */
    private static final Map<Integer, Map<Integer, Long>> WARMUP_NOTIFIED = new ConcurrentHashMap<>();

    public static final class NavigationDirective {
        public final Point targetPos;
        public final boolean consumedTick;

        NavigationDirective(Point targetPos, boolean consumedTick) {
            this.targetPos = targetPos;
            this.consumedTick = consumedTick;
        }
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
    // a portal that chains straight off another portal (see runSearch). The flag is part of the
    // dedup key, so a region reachable both ways is explored under both costs.
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
                                   int resultEdges) {
    }

    public static NavigationDirective resolveTarget(BotEntry entry, Point rawTargetPos, boolean runAiTick) {
        long startedAt = System.nanoTime();
        try {
            Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
            if (bot.getMap().getFootholds() == null) {
                AgentBotNavigationDebugStateRuntime.clearGraphWarmupFallback(entry);
                clearNavigation(entry);
                return new NavigationDirective(rawTargetPos, false);
            }
            if (bot.getMap().isSwim()) {
                // Swim maps don't use a swim-aware nav graph. Airborne motion is handled
                // by the swim integrator (tickSwimming); on platforms we still need
                // ledge-drops, ropes, and ground jumps. Engage the heuristic fallback —
                // it walks off ledges into water, picks up nearby ropes, and jumps onto
                // higher platforms when useful. tickSwimming consults targetPos directly,
                // so the same rawTargetPos works for both grounded and airborne paths.
                AgentBotNavigationDebugStateRuntime.setGraphWarmupFallback(entry, true);
                clearNavigation(entry);
                return new NavigationDirective(rawTargetPos, false);
            }

            AgentNavigationGraph graph = resolveActiveGraph(bot.getMap(), AgentBotMovementStateRuntime.movementProfile(entry));
            if (graph == null) {
                AgentNavigationGraphService.warmGraphAsync(bot.getMap(), AgentBotMovementStateRuntime.movementProfile(entry));
                AgentBotNavigationDebugStateRuntime.setGraphWarmupFallback(entry, true);
                notifyWarmup(entry, bot);
                AgentBotNavigationDebugStateRuntime.setLastDecision(entry, "graph-warmup");
                clearNavigation(entry);
                Point fallbackTarget = rawTargetPos != null ? new Point(rawTargetPos) : bot.getPosition();
                AgentBotNavigationDebugStateRuntime.recordPathLog(entry, captureTargetSnapshot(entry, rawTargetPos), -1, false, runAiTick);
                return new NavigationDirective(fallbackTarget, false);
            }
            if (AgentNavigationGraphService.peekGraph(bot.getMap(), AgentBotMovementStateRuntime.movementProfile(entry)) == null) {
                AgentNavigationGraphService.warmGraphAsync(bot.getMap(), AgentBotMovementStateRuntime.movementProfile(entry));
                AgentBotNavigationDebugStateRuntime.setLastDecision(entry, "graph-fallback-profile");
            }
            AgentBotNavigationDebugStateRuntime.clearGraphWarmupFallback(entry);
            Point botPos = bot.getPosition();
            int startRegionId = resolveCurrentRegionId(graph, entry, bot.getMap(), botPos);
            int targetRegionId = resolveTargetRegionId(graph, entry, bot.getMap(), rawTargetPos);
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
                edge = findNextEdge(graph, bot, startRegionId, targetRegionId, pathTargetPos);
                if (edge != null) {
                    AgentBotNavigationDebugStateRuntime.setActiveNavigationEdge(entry, edge);
                    AgentBotNavigationDebugStateRuntime.setNavTargetRegionId(entry, targetRegionId);
                }
            }

            if (edge == null) {
                AgentBotNavigationDebugStateRuntime.setLastDecision(entry, !runAiTick ? "no-ai"
                        : startRegionId < 0 || targetRegionId < 0 ? "no-region"
                        : startRegionId == targetRegionId ? "same-region" : "no-path");
                clearNavigation(entry);
                AgentBotNavigationDebugStateRuntime.recordPathLog(entry, captureTargetSnapshot(entry, rawTargetPos), startRegionId, false, runAiTick);
                return new NavigationDirective(rawTargetPos, false);
            }

            NavigationDirective executionDirective = tryExecuteEdge(graph, entry, bot, botPos, rawTargetPos, edge, runAiTick);
            if (executionDirective != null) {
                AgentBotNavigationDebugStateRuntime.setLastDecision(entry, "exec");
                AgentBotNavigationDebugStateRuntime.recordPathLog(entry, captureTargetSnapshot(entry, rawTargetPos), startRegionId, true, runAiTick);
                return executionDirective;
            }

            AgentBotNavigationDebugStateRuntime.setLastDecision(entry, edgeReused ? "reuse" : "new");
            AgentBotNavigationDebugStateRuntime.setNavWaypoint(
                    entry,
                    selectWaypoint(entry, graph, botPos, edge),
                    shouldUsePreciseTarget(graph, entry, botPos, edge));
            AgentBotNavigationDebugStateRuntime.recordPathLog(entry, captureTargetSnapshot(entry, rawTargetPos), startRegionId, false, runAiTick);
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
        AgentNavigationGraph graph = resolveActiveGraph(AgentBotRuntimeIdentityRuntime.botMap(entry), AgentBotMovementStateRuntime.movementProfile(entry));
        if (graph == null) {
            AgentNavigationGraphService.warmGraphAsync(AgentBotRuntimeIdentityRuntime.botMap(entry), AgentBotMovementStateRuntime.movementProfile(entry));
            return false;
        }
        Point botPos = AgentBotRuntimeIdentityRuntime.bot(entry).getPosition();
        int startRegionId = resolveCurrentRegionId(graph, entry, AgentBotRuntimeIdentityRuntime.botMap(entry), botPos);
        AgentNavigationGraph.Edge edge = reuseCommittedEdge(graph, entry, startRegionId,
                AgentBotNavigationDebugStateRuntime.navTargetRegionId(entry));
        if (edge == null) {
            AgentMovementStateResetService.clearNavigationState(entry);
            return false;
        }

        NavigationDirective directive = tryExecuteEdge(graph, entry, AgentBotRuntimeIdentityRuntime.bot(entry), botPos, rawTargetPos, edge, true);
        if (directive == null || !directive.consumedTick) {
            return false;
        }

        AgentBotNavigationDebugStateRuntime.setLastDecision(entry, "exec");
        return true;
    }

    private static void clearNavigation(BotEntry entry) {
        AgentMovementStateResetService.clearNavigationState(entry);
    }

    private static AgentMovementTargetSnapshot captureTargetSnapshot(BotEntry entry, Point rawTargetPos) {
        return AgentBotMovementTargetSideEffects.captureTargetSnapshot(entry, rawTargetPos);
    }

    private static void notifyWarmup(BotEntry entry, Character bot) {
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        if (owner == null) return;
        int ownerId = owner.getId();
        int mapId = bot.getMap().getId();
        long now = System.currentTimeMillis();
        Map<Integer, Long> byMap = WARMUP_NOTIFIED.get(ownerId);
        if (byMap != null) {
            Long last = byMap.get(mapId);
            if (last != null && (now - last) < 10_000L) return;
        }
        // Only count walkable footholds when we are about to send — lazy, inside throttle gate
        long walkable = bot.getMap().getFootholds().getAllFootholds().stream()
                .filter(fh -> !fh.isWall()).count();
        if (walkable < 100) return;
        WARMUP_NOTIFIED.computeIfAbsent(ownerId, k -> new ConcurrentHashMap<>()).put(mapId, now);
        owner.dropMessage(5, bot.getName() + " is warming map navigation cache, using fallback movement...");
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
        if (!runAiTick
                || edge == null
                || !AgentBotClimbStateRuntime.climbing(entry)
                || edge.type != AgentNavigationGraph.EdgeType.CLIMB
                || edge.launchStepX == 0
                || startRegionId < 0
                || targetRegionId < 0
                || startRegionId == targetRegionId) {
            return edge;
        }

        if (canExecuteClimbExitFromCurrentPosition(graph, bot.getMap(), botPos, edge)) {
            return edge;
        }

        AgentNavigationGraph.Edge bestEdge = findNextEdge(graph, bot, startRegionId, targetRegionId, targetPos);
        if (sameEdge(edge, bestEdge) || bestEdge == null) {
            return edge;
        }

        AgentBotNavigationDebugStateRuntime.setActiveNavigationEdge(entry, bestEdge);
        AgentBotNavigationDebugStateRuntime.setNavTargetRegionId(entry, targetRegionId);
        AgentBotNavigationDebugStateRuntime.clearNavTargetPosition(entry);
        AgentBotNavigationDebugStateRuntime.setNavPreciseTarget(entry, false);
        return bestEdge;
    }

    private static AgentNavigationGraph.Edge refreshCommittedGroundEdge(AgentNavigationGraph graph,
                                                                      BotEntry entry,
                                                                      Character bot,
                                                                      int startRegionId,
                                                                      int targetRegionId,
                                                                      Point targetPos,
                                                                      AgentNavigationGraph.Edge edge,
                                                                      boolean runAiTick) {
        if (!runAiTick
                || edge == null
                || AgentBotMovementStateRuntime.inAir(entry)
                || AgentBotClimbStateRuntime.climbing(entry)
                || startRegionId < 0
                || targetRegionId < 0
                || startRegionId == targetRegionId) {
            return edge;
        }

        AgentNavigationGraph.Edge bestEdge = findNextEdge(graph, bot, startRegionId, targetRegionId, targetPos);
        if (bestEdge == null || sameEdge(edge, bestEdge)) {
            return edge;
        }
        if (shouldRetainCommittedGroundEdge(edge, bestEdge)) {
            return edge;
        }

        AgentBotNavigationDebugStateRuntime.setActiveNavigationEdge(entry, bestEdge);
        AgentBotNavigationDebugStateRuntime.setNavTargetRegionId(entry, targetRegionId);
        AgentBotNavigationDebugStateRuntime.clearNavTargetPosition(entry);
        AgentBotNavigationDebugStateRuntime.setNavPreciseTarget(entry, false);
        return bestEdge;
    }

    static AgentNavigationGraph.Edge reuseCommittedEdge(AgentNavigationGraph graph,
                                                      BotEntry entry,
                                                      int startRegionId,
                                                      int targetRegionId) {
        AgentNavigationGraph.Edge edge = (AgentNavigationGraph.Edge) AgentBotNavigationDebugStateRuntime.activeNavigationEdge(entry);
        if (edge == null) {
            return null;
        }
        if (targetRegionId < 0) {
            return null;
        }
        int previousTargetRegionId = AgentBotNavigationDebugStateRuntime.navTargetRegionId(entry);
        // Update stored target in-place rather than discarding. The Y-snap offset causes
        // followBase.x to differ between AI and non-AI ticks, making targetRegionId fluctuate
        // even when the owner hasn't meaningfully moved. Relying on structural checks below
        // (start-region match, usability, arrival) is sufficient to detect actual invalidity.
        AgentBotNavigationDebugStateRuntime.setNavTargetRegionId(entry, targetRegionId);
        if (!isEdgeUsable(graph, AgentBotRuntimeIdentityRuntime.bot(entry), edge)) {
            return null;
        }
        if (AgentBotClimbStateRuntime.climbing(entry) && isRopeEntryEdge(graph, edge)) {
            return null;
        }
        if (startRegionId == edge.toRegionId && !AgentBotMovementStateRuntime.inAir(entry) && !AgentBotClimbStateRuntime.climbing(entry)
                && edge.fromRegionId != edge.toRegionId) {
            // Self-loop edges (intra-region portals) inherently start and end in the same
            // region. Completion is signalled by execution (tryExecutePortal teleporting the
            // bot), not by a region change — don't retire on region match.
            return null;
        }
        // Once the resolved target is back in the bot's current region, any committed edge that
        // would leave that region is stale. Keeping it causes follow/formation loops where the
        // bot repeatedly runs toward an old jump/drop/portal after the live follow target has
        // snapped back onto the current platform.
        if (!AgentBotMovementStateRuntime.inAir(entry) && !AgentBotClimbStateRuntime.climbing(entry)
                && startRegionId >= 0 && startRegionId == targetRegionId
                && edge.toRegionId != startRegionId) {
            return null;
        }
        if (startRegionId == edge.fromRegionId) {
            if (!AgentBotMovementStateRuntime.inAir(entry) && !AgentBotClimbStateRuntime.climbing(entry)
                    && previousTargetRegionId >= 0
                    && previousTargetRegionId != targetRegionId
                    && edge.toRegionId != targetRegionId) {
                return null;
            }
            return edge;
        }
        // While climbing, always keep the edge — findGroundFoothold gives false positives
        // (returns the platform below/behind the rope as the "current" region), which would
        // otherwise drop the exit edge the moment the bot enters the destination region's Y range.
        if (AgentBotClimbStateRuntime.climbing(entry) && (startRegionId < 0 || startRegionId != edge.toRegionId)) {
            return edge;
        }
        // DROP/JUMP arcs may enter the destination region before the bot touches down.
        // Keep the edge until landing. Only retain if the bot is in a region consistent with
        // this arc (destination or unmapped) — prevents looping in a wrong region mid-air.
        if (AgentBotMovementStateRuntime.inAir(entry) && (startRegionId < 0 || startRegionId == edge.toRegionId)
                && (edge.type == AgentNavigationGraph.EdgeType.DROP
                    || edge.type == AgentNavigationGraph.EdgeType.JUMP)) {
            return edge;
        }
        if (AgentBotMovementStateRuntime.inAir(entry) && edge.type == AgentNavigationGraph.EdgeType.CLIMB && edge.launchStepX != 0) {
            // Rope-exit jump arcs use the same sampled ballistic model as JUMP/DROP edges.
            // Keep the committed edge until the bot actually lands or grabs a rope again;
            // otherwise mid-air replans can steer the bot off the authored landing path.
            return edge;
        }
        return null;
    }

    private static NavigationDirective tryExecuteEdge(AgentNavigationGraph graph,
                                                      BotEntry entry,
                                                      Character bot,
                                                      Point botPos,
                                                      Point rawTargetPos,
                                                      AgentNavigationGraph.Edge edge,
                                                      boolean runAiTick) {
        if (!runAiTick) {
            return null;
        }

        return switch (edge.type) {
            case JUMP -> tryExecuteJump(graph, entry, bot, rawTargetPos, edge);
            case DROP -> tryExecuteDrop(graph, entry, bot, botPos, rawTargetPos, edge);
            case CLIMB -> tryExecuteClimb(graph, entry, bot, botPos, rawTargetPos, edge);
            case PORTAL -> isReadyForEdge(botPos, edge) ? tryExecutePortal(entry, bot, rawTargetPos, edge) : null;
            default -> null;
        };
    }

    private static NavigationDirective tryExecuteJump(AgentNavigationGraph graph,
                                                      BotEntry entry,
                                                      Character bot,
                                                      Point rawTargetPos,
                                                      AgentNavigationGraph.Edge edge) {
        if (AgentBotMovementStateRuntime.inAir(entry) || AgentBotClimbStateRuntime.climbing(entry)) {
            return null;
        }
        Point botPos = bot.getPosition();
        if (!canExecuteSelectedJumpFromCurrentPosition(graph, entry, bot.getMap(), botPos, edge)) {
            // Bot may be standing at the top of a rope region whose bottom is the jump entry.
            // Grab the rope and descend — tickClimbing will naturally drive toward edge.startPoint.
            if (edge.startPoint.y > botPos.y) {
                AgentNavigationGraph.Region fromRegion = graph.getRegion(edge.fromRegionId);
                if (fromRegion != null && fromRegion.isRopeRegion) {
                    Rope rope = findRopeForRegion(bot.getMap(), fromRegion);
                    if (rope != null && canGrabRopeAtCurrentPosition(botPos, rope)) {
                        // Attach at bot's current Y — tickClimbing will drive it down to startPoint.
                        // Using edge.startPoint.y would teleport the bot rather than letting it climb.
                        startClimbing(entry, bot, rope, botPos.y);
                        return new NavigationDirective(rawTargetPos, true);
                    }
                }
            }
            AgentBotNavigationDebugStateRuntime.setLastEdgeBlockReason(entry, "jump-pos");
            return null;
        }

        AgentBotNavigationDebugStateRuntime.clearLastEdgeBlockReason(entry);
        setEdgeExecutionTarget(entry, edge);
        AgentJumpActionService.initiateJump(entry, bot, edge.launchStepX);
        return new NavigationDirective(rawTargetPos, true);
    }

    private static NavigationDirective tryExecuteDrop(AgentNavigationGraph graph,
                                                      BotEntry entry,
                                                      Character bot,
                                                      Point botPos,
                                                      Point rawTargetPos,
                                                      AgentNavigationGraph.Edge edge) {
        if (AgentBotMovementStateRuntime.inAir(entry) || AgentBotClimbStateRuntime.climbing(entry) || AgentBotMovementStateRuntime.downJumpPending(entry)) {
            return null;
        }

        if (edge.launchStepX != 0) {
            // Walk-off drops are not an explicit action. Keep steering in the authored direction
            // and let ground physics carry the bot into a fall with preserved momentum.
            return null;
        }

        if (!canExecuteDropFromCurrentPosition(graph, bot.getMap(), botPos, edge)) {
            return null;
        }

        setEdgeExecutionTarget(entry, edge);
        AgentQueuedMovementActionService.queueDownJump(entry, bot);
        AgentMovementBroadcastService.broadcastMovement(entry);
        return new NavigationDirective(rawTargetPos, true);
    }

    private static NavigationDirective tryExecuteClimb(AgentNavigationGraph graph,
                                                       BotEntry entry,
                                                       Character bot,
                                                       Point botPos,
                                                       Point rawTargetPos,
                                                       AgentNavigationGraph.Edge edge) {
        if (AgentBotMovementStateRuntime.inAir(entry) || AgentBotMovementStateRuntime.downJumpPending(entry)) {
            return null;
        }

        if (AgentBotClimbStateRuntime.climbing(entry)) {
            return tryExecuteClimbExit(graph, entry, bot, botPos, rawTargetPos, edge);
        } else {
            return tryExecuteClimbEntry(graph, entry, bot, botPos, rawTargetPos, edge);
        }
    }

    private static NavigationDirective tryExecuteClimbEntry(AgentNavigationGraph graph,
                                                             BotEntry entry,
                                                             Character bot,
                                                             Point botPos,
                                                             Point rawTargetPos,
                                                             AgentNavigationGraph.Edge edge) {
        AgentNavigationGraph.Region toRegion = graph.getRegion(edge.toRegionId);
        Rope rope = findRopeForRegion(bot.getMap(), toRegion);
        if (rope == null) {
            return null;
        }
        if (!canExecuteClimbEntryFromCurrentPosition(bot.getMap(), botPos, edge, rope)) {
            AgentBotNavigationDebugStateRuntime.setLastEdgeBlockReason(entry, "climb-pos");
            return null;
        }

        if (canGrabRopeAtCurrentPosition(botPos, rope)) {
            // Bot is already within the rope's Y range — attach at its current Y, not the edge
            // endPoint. Using endPoint.y (rope top) would teleport a bot at the bottom of the
            // rope all the way to the top instantly.
            AgentBotNavigationDebugStateRuntime.clearLastEdgeBlockReason(entry);
            startClimbing(entry, bot, rope, botPos.y);
            return new NavigationDirective(rawTargetPos, true);
        }
        if (canAttachToRopeFromTopPlatform(edge, botPos, rope)) {
            AgentBotNavigationDebugStateRuntime.clearLastEdgeBlockReason(entry);
            startClimbing(entry, bot, rope, edge.endPoint.y);
            return new NavigationDirective(rawTargetPos, true);
        }
        if (canGrabRopeFromTopPlatform(edge, botPos, rope)) {
            // Top-of-rope entry is a separate intent from down-jump. Queue it and let grounded
            // physics consume the request on the next tick, just like other input-driven actions.
            AgentBotNavigationDebugStateRuntime.clearLastEdgeBlockReason(entry);
            AgentQueuedMovementActionService.queueTopRopeEntry(entry, bot, rope, edge.endPoint.y);
            AgentMovementBroadcastService.broadcastMovement(entry);
            return new NavigationDirective(rawTargetPos, true);
        }

        if (canExecuteGroundRopeJumpEntryFromCurrentPosition(botPos, edge)) {
            AgentBotNavigationDebugStateRuntime.clearLastEdgeBlockReason(entry);
            AgentJumpActionService.initiateRopeJump(entry, bot, edge.launchStepX);
            return new NavigationDirective(rawTargetPos, true);
        }

        AgentBotNavigationDebugStateRuntime.setLastEdgeBlockReason(entry, "climb-reach");
        return null;
    }

    private static NavigationDirective tryExecuteClimbExit(AgentNavigationGraph graph,
                                                            BotEntry entry,
                                                            Character bot,
                                                            Point botPos,
                                                            Point rawTargetPos,
                                                            AgentNavigationGraph.Edge edge) {
        if (!canExecuteClimbExitFromCurrentPosition(graph, bot.getMap(), botPos, edge)) {
            return null;
        }
        AgentNavigationGraph.Region toRegion = graph.getRegion(edge.toRegionId);

        if (toRegion != null && toRegion.isRopeRegion) {
            // Rope-to-rope: jump to the other rope
            Rope targetRope = findRopeForRegion(bot.getMap(), toRegion);
            if (targetRope == null || AgentClimbMovementPolicy.sameRope(AgentBotClimbStateRuntime.climbRope(entry), targetRope)) {
                return null;
            }
            AgentClimbMovementService.jumpToRope(entry, bot, edge.launchStepX);
            return new NavigationDirective(rawTargetPos, true);
        }

        if (edge.launchStepX == 0) {
            // launchStepX==0 means step off the top of the rope onto the foothold above.
            // Physics already handles this: resolveClimbBoundary lands the bot when it reaches
            // topY. Nav just lets the bot climb — the edge completes when the bot transitions
            // to the destination region after physics lands it.
            return null;
        }

        // Jump off rope
        Rope sourceRope = findRopeForRegion(bot.getMap(), graph.getRegion(edge.fromRegionId));
        if (isTopRopeJumpExitReady(sourceRope, botPos, edge) && botPos.y != edge.startPoint.y) {
            startClimbing(entry, bot, sourceRope, edge.startPoint.y);
        }
        AgentClimbMovementService.jumpOffRope(entry, bot, edge.launchStepX);
        return new NavigationDirective(rawTargetPos, true);
    }

    static boolean canExecuteDropFromCurrentPosition(AgentNavigationGraph graph,
                                                     MapleMap map,
                                                     Point botPos,
                                                     AgentNavigationGraph.Edge edge) {
        if (edge.type != AgentNavigationGraph.EdgeType.DROP) {
            return false;
        }
        if (edge.launchStepX != 0) {
            return false;
        }
        if (!isWithinDropLaunchWindow(graph, botPos, edge)) {
            return false;
        }
        return true;
    }

    private static NavigationDirective tryExecutePortal(BotEntry entry,
                                                        Character bot,
                                                        Point rawTargetPos,
                                                        AgentNavigationGraph.Edge edge) {
        if (AgentBotNavigationDebugStateRuntime.portalUseOnCooldown(entry, System.currentTimeMillis())) {
            return null;
        }
        if (!usePortal(bot, edge.portalId)) {
            return null;
        }

        AgentBotNavigationDebugStateRuntime.setPortalUseCooldownUntilMs(
                entry, System.currentTimeMillis() + PORTAL_USE_COOLDOWN_MS);
        clearNavigation(entry);
        AgentMovementStateResetService.resetEntryState(entry);
        return new NavigationDirective(rawTargetPos, true);
    }

    private static boolean shouldUsePreciseTarget(AgentNavigationGraph graph,
                                                  BotEntry entry,
                                                  Point botPos,
                                                  AgentNavigationGraph.Edge edge) {
        if (AgentBotMovementStateRuntime.inAir(entry)) {
            return false;
        }
        return switch (edge.type) {
            case WALK -> shouldUsePreciseWalkTarget(edge);
            case JUMP -> !canExecuteSelectedJumpFromCurrentPosition(graph, entry, AgentBotRuntimeIdentityRuntime.botMap(entry), botPos, edge);
            case DROP -> edge.launchStepX == 0
                    && !canExecuteDropFromCurrentPosition(graph, AgentBotRuntimeIdentityRuntime.botMap(entry), botPos, edge);
            case CLIMB -> AgentBotClimbStateRuntime.climbing(entry)
                    ? edge.launchStepX != 0
                    && !canExecuteClimbExitFromCurrentPosition(graph, AgentBotRuntimeIdentityRuntime.botMap(entry), botPos, edge)
                    : !canExecuteClimbEntryFromCurrentPosition(AgentBotRuntimeIdentityRuntime.botMap(entry), botPos, edge,
                    findRopeForRegion(AgentBotRuntimeIdentityRuntime.botMap(entry), graph.getRegion(edge.toRegionId)));
            case PORTAL -> !isReadyForEdge(botPos, edge);
        };
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
        List<AgentNavigationGraph.Edge> path = findPath(graph, bot.getMap(), bot.getPosition(), startRegionId, targetRegionId, targetPos);
        if (path.isEmpty()) {
            return null;
        }
        return collapseLeadingWalkEdges(path);
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
        return runSearch(graph, map, startPos, startRegionId, targetRegionId, targetPos,
                pathfindCaller, useAdmissibleHeuristic, true).path();
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
        long startedAt = System.nanoTime();
        PathfindProfile profile = null;
        try {
            PriorityQueue<SearchNode> open = new PriorityQueue<>(Comparator.comparingInt(node -> node.score));
            Map<SearchState, Integer> gScore = new HashMap<>();
            Map<SearchState, SearchState> cameFrom = new HashMap<>();
            Map<SearchState, AgentNavigationGraph.Edge> cameByEdge = new HashMap<>();
            SearchState startState = new SearchState(startRegionId, new Point(startPos), false);
            SearchState bestGoalState = null;
            int bestGoalCost = Integer.MAX_VALUE;
            int expandedNodes = 0;
            int staleNodes = 0;
            int edgeChecks = 0;
            int usableEdges = 0;
            int relaxations = 0;
            int openPeak = 1;

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
                    if (!isEdgeUsable(graph, map, edge)) {
                        continue;
                    }
                    usableEdges++;

                    boolean isPortal = edge.type == AgentNavigationGraph.EdgeType.PORTAL;
                    // Portals are free on their own (edge.cost == 0). Charge PORTAL_USE_COOLDOWN_MS
                    // only when the bot enters a portal *through the exit* of the one it just took —
                    // i.e. it landed on a portal and immediately re-enters without walking. A
                    // viaPortal state's point IS the previous portal's exit, so this is exactly when
                    // that exit coincides with this portal's entry. That covers the "return to old
                    // position" round-trip and co-located A>B>C hops, but NOT A>B>walk>C>D (the bot
                    // walked off the exit first, so the entry points differ and it stays free).
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
                }
            }

            List<AgentNavigationGraph.Edge> path = reconstructPath(startState, bestGoalState, cameFrom, cameByEdge);
            profile = new PathfindProfile(
                    System.nanoTime() - startedAt,
                    expandedNodes,
                    staleNodes,
                    edgeChecks,
                    usableEdges,
                    relaxations,
                    openPeak,
                    bestGoalCost,
                    path.size());
            boolean usesPortal = false;
            for (AgentNavigationGraph.Edge edge : path) {
                if (edge.type == AgentNavigationGraph.EdgeType.PORTAL) {
                    usesPortal = true;
                    break;
                }
            }
            return new SearchOutcome(path, bestGoalCost, expandedNodes, usesPortal);
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
                            0);
                }
                logSlowPathfind(graph, map, startPos, startRegionId, targetRegionId, targetPos, pathfindCaller, profile);
                AgentPerformanceMonitor.recordPathfind(pathfindCaller, System.nanoTime() - startedAt);
            }
        }
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
                "Slow bot pathfind: caller={} took {} ms map={} startRegion={} targetRegion={} regions={} startOut={} startPos=({}, {}) targetPos=({}, {}) expanded={} stale={} edgeChecks={} usableEdges={} relaxations={} openPeak={} bestGoalCost={} resultEdges={}",
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
                profile.resultEdges());
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

    private static boolean isEdgeUsable(AgentNavigationGraph graph, MapleMap map, AgentNavigationGraph.Edge edge) {
        return AgentNavigationPathService.isEdgeUsable(graph, map, edge);
    }

    private static boolean usePortal(Character bot, int portalId) {
        Portal portal = bot.getMap().getPortal(portalId);
        if (portal == null || !portal.getPortalStatus()) {
            return false;
        }

        int oldMapId = bot.getMapId();
        Point oldPos = bot.getPosition();
        portal.enterPortal(bot.getClient());
        return bot.getMapId() != oldMapId || !bot.getPosition().equals(oldPos);
    }

    private static boolean isReadyForEdge(Point botPos, AgentNavigationGraph.Edge edge) {
        return AgentNavigationEdgeReadinessService.isReadyForEdge(botPos, edge);
    }

    static boolean canExecuteJumpFromCurrentPosition(AgentNavigationGraph graph,
                                                     MapleMap map,
                                                     Point botPos,
                                                     AgentNavigationGraph.Edge edge) {
        if (edge.type != AgentNavigationGraph.EdgeType.JUMP) {
            return false;
        }
        return isWithinJumpLaunchWindow(graph, botPos, edge);
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
        return Math.abs(botPos.x - launchX) <= tolerance;
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

    private static int intraRegionTravelCost(AgentNavigationGraph graph, Point from, Point to) {
        return AgentNavigationPathService.intraRegionTravelCost(graph, from, to);
    }

    private static int intraRegionTravelCost(AgentNavigationGraph graph, int regionId, Point from, Point to) {
        return AgentNavigationPathService.intraRegionTravelCost(graph, regionId, from, to);
    }

    private static int heuristic(AgentNavigationGraph graph, Point from, Point targetPos) {
        return AgentNavigationPathService.heuristic(graph, from, targetPos);
    }

    static boolean shouldUsePreciseWalkTarget(AgentNavigationGraph.Edge edge) {
        return AgentNavigationPathService.shouldUsePreciseWalkTarget(edge);
    }

    private static boolean canGrabRopeAtCurrentPosition(Point botPos, Rope rope) {
        return AgentNavigationRopeEdgeService.canGrabRopeAtCurrentPosition(botPos, rope);
    }

    private static boolean canAttachToRopeFromTopPlatform(AgentNavigationGraph.Edge edge, Point botPos, Rope rope) {
        return AgentNavigationRopeEdgeService.canAttachToRopeFromTopPlatform(edge, botPos, rope);
    }

    private static boolean canGrabRopeFromTopPlatform(AgentNavigationGraph.Edge edge, Point botPos, Rope rope) {
        return AgentNavigationRopeEdgeService.canGrabRopeFromTopPlatform(edge, botPos, rope);
    }

    private static boolean canExecuteClimbEntryFromCurrentPosition(MapleMap map,
                                                                   Point botPos,
                                                                   AgentNavigationGraph.Edge edge,
                                                                   Rope rope) {
        return AgentNavigationRopeEdgeService.canExecuteClimbEntryFromCurrentPosition(botPos, edge, rope);
    }

    private static boolean canExecuteGroundRopeJumpEntryFromCurrentPosition(Point botPos,
                                                                           AgentNavigationGraph.Edge edge) {
        return AgentNavigationRopeEdgeService.canExecuteGroundRopeJumpEntryFromCurrentPosition(botPos, edge);
    }

    private static boolean canExecuteClimbExitFromCurrentPosition(AgentNavigationGraph graph,
                                                                  MapleMap map,
                                                                  Point botPos,
                                                                  AgentNavigationGraph.Edge edge) {
        if (edge.type != AgentNavigationGraph.EdgeType.CLIMB) {
            return false;
        }

        if (edge.launchStepX != 0 && botPos.y != edge.startPoint.y) {
            Rope rope = findRopeForRegion(map, graph.getRegion(edge.fromRegionId));
            if (!isTopRopeJumpExitReady(rope, botPos, edge)) {
                // Rope-exit jump edges are authored from a specific climb height. Launching from
                // any other Y changes the ballistic arc; climb movement reaches the authored
                // first climbable pixel before this executes.
                return false;
            }
        }

        AgentNavigationGraph.Region toRegion = graph.getRegion(edge.toRegionId);
        if (toRegion != null && toRegion.isRopeRegion) {
            return Math.abs(botPos.y - edge.startPoint.y) <= AgentMovementPhysicsConfig.configuredJumpYThreshold() * 2;
        }

        if (edge.launchStepX == 0) {
            Rope rope = findRopeForRegion(map, graph.getRegion(edge.fromRegionId));
            return rope != null && isTopStepOffExit(rope, botPos, edge);
        }

        return Math.abs(botPos.y - edge.startPoint.y) <= AgentMovementPhysicsConfig.configuredJumpYThreshold() * 2;
    }

    private static boolean isTopRopeJumpExitReady(Rope rope, Point botPos, AgentNavigationGraph.Edge edge) {
        return AgentNavigationRopeEdgeService.isTopRopeJumpExitReady(rope, botPos, edge);
    }

    private static void startClimbing(BotEntry entry, Character bot, Rope rope, int climbY) {
        AgentRopeMovementService.attachToRope(entry, bot, rope, climbY);
        AgentMovementBroadcastService.broadcastMovement(entry);
    }

    private static void setEdgeExecutionTarget(BotEntry entry, AgentNavigationGraph.Edge edge) {
        AgentBotNavigationDebugStateRuntime.setNavWaypoint(entry, edge.endPoint, false);
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
