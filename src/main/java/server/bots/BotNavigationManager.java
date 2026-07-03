package server.bots;

import server.agents.capabilities.navigation.AgentNavigationGraphService;

import server.agents.capabilities.navigation.AgentNavigationGraph;

import server.agents.runtime.AgentPerformanceMonitor;

import server.agents.capabilities.movement.AgentClimbMovementPolicy;
import server.agents.capabilities.movement.AgentClimbMovementService;
import server.agents.capabilities.movement.AgentJumpActionService;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementKinematicsService;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.movement.AgentMovementStateResetService;

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
import java.util.concurrent.ThreadLocalRandom;

public final class BotNavigationManager {
    private static final Logger log = LoggerFactory.getLogger(BotNavigationManager.class);
    private static final int JUMP_READY_X_TOLERANCE = 10;
    private static final int EDGE_READY_X_TOLERANCE = 14;
    private static final int NO_MOVEMENT_WALK_TOLERANCE = 4;
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
        BotPhysicsEngine.queueDownJump(entry, bot);
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
            BotPhysicsEngine.queueTopRopeEntry(entry, bot, rope, edge.endPoint.y);
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
        AgentNavigationGraph.Region fromRegion = graph.getRegion(edge.fromRegionId);
        if (fromRegion == null || fromRegion.isRopeRegion) {
            return new Point(edge.startPoint);
        }
        int targetX = entry == null
                ? edge.containsLaunchX(botPos.x) ? botPos.x : botPos.x < edge.launchMinX ? edge.launchMinX : edge.launchMaxX
                : selectedJumpLaunchX(entry, graph, edge);
        return fromRegion.pointAt(targetX);
    }

    static Point selectClimbWaypoint(BotEntry entry, Point botPos, AgentNavigationGraph.Edge edge) {
        AgentNavigationGraph graph = resolveActiveGraph(AgentBotRuntimeIdentityRuntime.botMap(entry), AgentBotMovementStateRuntime.movementProfile(entry));
        return selectClimbWaypoint(graph, entry, botPos, edge);
    }

    static Point selectClimbWaypoint(AgentNavigationGraph graph, BotEntry entry, Point botPos, AgentNavigationGraph.Edge edge) {
        if (AgentBotMovementStateRuntime.inAir(entry)) {
            return new Point(edge.endPoint);
        }
        if (AgentBotClimbStateRuntime.climbing(entry) && edge.launchStepX != 0) {
            // Jump-off and rope-to-rope exits: only hold position when the exit can execute
            // immediately; otherwise keep steering toward the authored launch anchor.
            // Graphgen and physics both treat edge.startPoint as the required on-rope launch Y;
            // steering toward edge.endPoint here would be a runtime-only model mismatch because
            // a climbing bot cannot physically approach the off-rope landing point.
            if (graph != null && canExecuteClimbExitFromCurrentPosition(graph, AgentBotRuntimeIdentityRuntime.botMap(entry), botPos, edge)) {
                return new Point(botPos);
            }
            return new Point(edge.startPoint);
        }
        if (AgentBotClimbStateRuntime.climbing(entry)) {
            // launchStepX==0: keep holding climb direction on the rope and let physics dismount
            // the bot at the boundary. The on-rope steering target should stay on the rope X;
            // trying to snap to an off-rope landing point is a runtime-only constraint and can
            // re-clamp the bot back onto the rope top/bottom.
            Rope climbRope = AgentBotClimbStateRuntime.climbRope(entry);
            int ropeX = climbRope != null ? climbRope.x() : edge.startPoint.x;
            return new Point(ropeX, edge.endPoint.y);
        }
        return new Point(edge.startPoint);
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
            AgentNavigationGraph.Region fromRegion = graph != null ? graph.getRegion(edge.fromRegionId) : null;
            if (fromRegion == null || fromRegion.isRopeRegion) {
                return new Point(edge.startPoint);
            }
            int targetX = edge.containsLaunchX(botPos.x)
                    ? botPos.x
                    : botPos.x < edge.launchMinX ? edge.launchMinX : edge.launchMaxX;
            return fromRegion.pointAt(targetX);
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
                (BotPhysicsEngine.GroundTravelState) AgentBotMovementPhysicsStateRuntime.groundTravelState(entry),
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
        if (botPos == null || edge == null || edge.launchStepX == 0) {
            return false;
        }

        int direction = Integer.signum(edge.launchStepX);
        return direction > 0
                ? botPos.x >= edge.startPoint.x
                : botPos.x <= edge.startPoint.x;
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

    private static boolean isEdgeUsable(AgentNavigationGraph graph, Character bot, AgentNavigationGraph.Edge edge) {
        return isEdgeUsable(graph, bot.getMap(), edge);
    }

    private static boolean sameEdge(AgentNavigationGraph.Edge left, AgentNavigationGraph.Edge right) {
        return left == right || (left != null
                && right != null
                && left.fromRegionId == right.fromRegionId
                && left.toRegionId == right.toRegionId
                && left.type == right.type
                && left.launchMinX == right.launchMinX
                && left.launchMaxX == right.launchMaxX
                && left.launchStepX == right.launchStepX
                && left.portalId == right.portalId
                && left.ropeX == right.ropeX
                && left.ropeTopY == right.ropeTopY
                && left.ropeBottomY == right.ropeBottomY
                && left.startPoint.equals(right.startPoint)
                && left.endPoint.equals(right.endPoint));
    }

    static boolean shouldRetainCommittedGroundEdge(AgentNavigationGraph.Edge current,
                                                   AgentNavigationGraph.Edge replacement) {
        if (current == null || replacement == null) {
            return false;
        }
        if (current.fromRegionId != replacement.fromRegionId
                || current.toRegionId != replacement.toRegionId) {
            return false;
        }
        // Equivalent first exits into the same downstream region can trade off a few pixels of
        // approach cost as the bot shuffles on the source platform. Replacing the committed edge
        // every AI tick creates oscillation loops like the John 2026-05-01 down-jump trace,
        // where nav flips between a straight DROP and a nearby JUMP before either can execute.
        return current.type != AgentNavigationGraph.EdgeType.WALK
                && replacement.type != AgentNavigationGraph.EdgeType.WALK;
    }

    private static boolean isEdgeUsable(AgentNavigationGraph graph, MapleMap map, AgentNavigationGraph.Edge edge) {
        return switch (edge.type) {
            case WALK, JUMP, DROP, CLIMB -> true;
            case PORTAL -> {
                Portal portal = map.getPortal(edge.portalId);
                yield portal != null && portal.getPortalStatus();
            }
        };
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
        int dx = Math.abs(botPos.x - edge.startPoint.x);
        int dy = Math.abs(botPos.y - edge.startPoint.y);

        return switch (edge.type) {
            case JUMP -> dx <= JUMP_READY_X_TOLERANCE && dy <= AgentMovementPhysicsConfig.configuredJumpYThreshold();
            case DROP, CLIMB, PORTAL -> dx <= EDGE_READY_X_TOLERANCE && dy <= AgentMovementPhysicsConfig.configuredJumpYThreshold() * 2;
            default -> dx <= AgentMovementPhysicsConfig.configuredStopDist() + 8
                    && dy <= AgentMovementPhysicsConfig.configuredJumpYThreshold() * 2;
        };
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
            if (!BotPhysicsEngine.isWalkableEndpointStep(Math.abs(current.x - previous.x), current.y - previous.y)) {
                return false;
            }
            previous = current;
        }
        return true;
    }

    static boolean isWithinJumpLaunchWindow(AgentNavigationGraph graph,
                                            Point botPos,
                                            AgentNavigationGraph.Edge edge) {
        if (botPos == null || edge.type != AgentNavigationGraph.EdgeType.JUMP || !edge.containsLaunchX(botPos.x)) {
            return false;
        }

        AgentNavigationGraph.Region fromRegion = graph.getRegion(edge.fromRegionId);
        if (fromRegion == null) {
            return false;
        }

        Point expectedLaunchPoint = fromRegion.pointAt(botPos.x);
        return Math.abs(botPos.y - expectedLaunchPoint.y) <= AgentMovementPhysicsConfig.configuredJumpYThreshold();
    }

    static boolean isWithinDropLaunchWindow(AgentNavigationGraph graph,
                                            Point botPos,
                                            AgentNavigationGraph.Edge edge) {
        if (botPos == null
                || edge.type != AgentNavigationGraph.EdgeType.DROP
                || edge.launchStepX != 0
                || !edge.containsLaunchX(botPos.x)) {
            return false;
        }

        if (graph == null) {
            return Math.abs(botPos.y - edge.startPoint.y) <= AgentMovementPhysicsConfig.configuredJumpYThreshold();
        }

        AgentNavigationGraph.Region fromRegion = graph.getRegion(edge.fromRegionId);
        if (fromRegion == null || fromRegion.isRopeRegion) {
            return false;
        }

        Point expectedLaunchPoint = fromRegion.pointAt(botPos.x);
        return Math.abs(botPos.y - expectedLaunchPoint.y) <= AgentMovementPhysicsConfig.configuredJumpYThreshold();
    }

    private static int selectedJumpLaunchX(BotEntry entry,
                                           AgentNavigationGraph graph,
                                           AgentNavigationGraph.Edge edge) {
        if (entry == null || graph == null || edge == null || edge.type != AgentNavigationGraph.EdgeType.JUMP) {
            return edge != null ? edge.startPoint.x : 0;
        }
        AgentNavigationGraph.Region fromRegion = graph.getRegion(edge.fromRegionId);
        if (fromRegion == null || fromRegion.isRopeRegion) {
            return edge.startPoint.x;
        }
        int cachedLaunchX = AgentBotNavigationDebugStateRuntime.navJumpLaunchX(entry);
        if (AgentBotNavigationDebugStateRuntime.matchesNavJumpLaunchEdge(entry, edge)
                && cachedLaunchX >= edge.launchMinX
                && cachedLaunchX <= edge.launchMaxX) {
            return cachedLaunchX;
        }

        int minX = Math.max(edge.launchMinX, fromRegion.minX);
        int maxX = Math.min(edge.launchMaxX, fromRegion.maxX);
        if (minX > maxX) {
            minX = edge.launchMinX;
            maxX = edge.launchMaxX;
        }

        int width = Math.max(0, maxX - minX);
        int margin = Math.min(width / 2, Math.max(1,
                AgentMovementKinematicsService.walkStep(AgentBotRuntimeIdentityRuntime.botMap(entry), AgentBotMovementStateRuntime.movementProfile(entry)) * 2));
        int randomMinX = minX + margin;
        int randomMaxX = maxX - margin;
        if (randomMinX > randomMaxX) {
            randomMinX = minX;
            randomMaxX = maxX;
        }

        int selectedX = randomMinX >= randomMaxX
                ? randomMinX
                : ThreadLocalRandom.current().nextInt(randomMinX, randomMaxX + 1);
        AgentBotNavigationDebugStateRuntime.rememberNavJumpLaunch(entry, edge, selectedX);
        return selectedX;
    }

    private static int intraRegionTravelCost(AgentNavigationGraph graph, Point from, Point to) {
        int dx = Math.abs(to.x - from.x);
        return Math.max(0, (int) Math.round((dx * 1000.0) / Math.max(1.0, graph.movementProfile.walkVelocityPxs())));
    }

    private static int intraRegionTravelCost(AgentNavigationGraph graph, int regionId, Point from, Point to) {
        AgentNavigationGraph.Region region = graph.getRegion(regionId);
        if (region != null && region.isRopeRegion) {
            int travel = Math.abs(to.y - from.y);
            return Math.max(0, (int) Math.round((travel * 1000.0) / Math.max(1, AgentMovementPhysicsConfig.configuredClimbSpeedPxs())));
        }
        return intraRegionTravelCost(graph, from, to);
    }

    private static int heuristic(AgentNavigationGraph graph, Point from, Point targetPos) {
        return intraRegionTravelCost(graph, from, targetPos);
    }

    static boolean shouldUsePreciseWalkTarget(AgentNavigationGraph.Edge edge) {
        return edge != null
                && edge.type == AgentNavigationGraph.EdgeType.WALK
                && !isNoMovementWalk(edge.startPoint, edge.endPoint);
    }

    private static boolean isNoMovementWalk(Point start, Point end) {
        return Math.abs(end.x - start.x) <= NO_MOVEMENT_WALK_TOLERANCE
                && Math.abs(end.y - start.y) <= NO_MOVEMENT_WALK_TOLERANCE;
    }

    private static boolean canGrabRopeAtCurrentPosition(Point botPos, Rope rope) {
        return Math.abs(botPos.x - rope.x()) <= AgentMovementPhysicsConfig.configuredRopeGrabX()
                && botPos.y >= BotPhysicsEngine.firstClimbableY(rope)
                && botPos.y <= rope.bottomY();
    }

    private static boolean canAttachToRopeFromTopPlatform(AgentNavigationGraph.Edge edge, Point botPos, Rope rope) {
        return Math.abs(botPos.x - rope.x()) <= AgentMovementPhysicsConfig.configuredRopeGrabX()
                && edge.endPoint.y == BotPhysicsEngine.firstClimbableY(rope)
                && botPos.y < rope.topY()
                && rope.topY() - botPos.y <= AgentMovementPhysicsConfig.configuredMaxSnapDrop();
    }

    private static boolean canGrabRopeFromTopPlatform(AgentNavigationGraph.Edge edge, Point botPos, Rope rope) {
        return edge.startPoint.y <= rope.topY() + AgentMovementPhysicsConfig.configuredJumpYThreshold()
                && Math.abs(botPos.x - rope.x()) <= AgentMovementPhysicsConfig.configuredRopeGrabX();
    }

    private static boolean canExecuteClimbEntryFromCurrentPosition(MapleMap map,
                                                                   Point botPos,
                                                                   AgentNavigationGraph.Edge edge,
                                                                   Rope rope) {
        return rope != null && (canGrabRopeAtCurrentPosition(botPos, rope)
                || canAttachToRopeFromTopPlatform(edge, botPos, rope)
                || canGrabRopeFromTopPlatform(edge, botPos, rope)
                || canExecuteGroundRopeJumpEntryFromCurrentPosition(botPos, edge));
    }

    private static boolean canExecuteGroundRopeJumpEntryFromCurrentPosition(Point botPos,
                                                                           AgentNavigationGraph.Edge edge) {
        if (botPos == null || edge == null || edge.type != AgentNavigationGraph.EdgeType.CLIMB) {
            return false;
        }
        return edge.containsLaunchX(botPos.x)
                && Math.abs(botPos.y - edge.startPoint.y) <= AgentMovementPhysicsConfig.configuredJumpYThreshold() * 2;
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
        // Top-of-rope tolerance window only: the bot lands at firstClimbableY when grabbing
        // from above (canTopGrab/canTopStep), and the launch arc is invariant within the first
        // climbStep below the top. Non-top anchors are single-point launch windows whose arcs
        // are precomputed by simulateRopeJumpLanding — launching from any other Y misses the
        // destination. The bot reaches non-top anchors exactly via the precise-target snap in
        // AgentClimbMovementService.shouldSnapToClimbTarget (sub-tick clamp; the only one in the
        // physics path), so the bypass `botPos.y == edge.startPoint.y` in
        // canExecuteClimbExitFromCurrentPosition covers those without tolerance here.
        if (rope == null || botPos == null || edge == null || edge.launchStepX == 0) {
            return false;
        }
        int firstClimbableY = BotPhysicsEngine.firstClimbableY(rope);
        return edge.startPoint.x == rope.x()
                && edge.startPoint.y == firstClimbableY
                && botPos.x == rope.x()
                && botPos.y >= firstClimbableY
                && botPos.y <= firstClimbableY + AgentMovementKinematicsService.climbStepPerTick() + 2;
    }

    private static void startClimbing(BotEntry entry, Character bot, Rope rope, int climbY) {
        BotPhysicsEngine.attachToRope(entry, bot, rope, climbY);
        AgentMovementBroadcastService.broadcastMovement(entry);
    }

    private static void setEdgeExecutionTarget(BotEntry entry, AgentNavigationGraph.Edge edge) {
        AgentBotNavigationDebugStateRuntime.setNavWaypoint(entry, edge.endPoint, false);
    }

    private static Point adjustPathTarget(BotEntry entry,
                                          AgentNavigationGraph graph,
                                          int targetRegionId,
                                          Point rawTargetPos) {
        if (rawTargetPos == null || !AgentBotModeStateRuntime.grinding(entry) || targetRegionId < 0) {
            return rawTargetPos;
        }

        AgentNavigationGraph.Region targetRegion = graph.getRegion(targetRegionId);
        if (targetRegion == null || targetRegion.isRopeRegion) {
            return rawTargetPos;
        }

        int safeLeft = targetRegion.minX + AgentMovementPhysicsConfig.configuredGrindEdgeMargin();
        int safeRight = targetRegion.maxX - AgentMovementPhysicsConfig.configuredGrindEdgeMargin();
        if (safeLeft >= safeRight) {
            return rawTargetPos;
        }

        int clampedX = Math.max(safeLeft, Math.min(safeRight, rawTargetPos.x));
        return targetRegion.pointAt(clampedX);
    }

    private static int landingRegionId(AgentNavigationGraph graph, BotPhysicsEngine.JumpLanding landing) {
        if (landing == null) {
            return -1;
        }
        return graph.regionIdByFootholdId.getOrDefault(landing.foothold().getId(), -1);
    }

    public static int resolveCurrentRegionId(AgentNavigationGraph graph,
                                      BotEntry entry,
                                      MapleMap map,
                                      Point botPos) {
        if (AgentBotClimbStateRuntime.climbing(entry) || (AgentBotRuntimeIdentityRuntime.hasBot(entry) && CharacterStance.isClimbing(AgentBotRuntimeIdentityRuntime.bot(entry).getStance()))) {
            // Rope climbing state is authoritative. Ground lookup below a rope often resolves to
            // the nearby platform instead of the rope region, which can replan from the wrong side
            // of the rope and bounce between entry/exit climb edges.
            Rope climbRope = AgentBotClimbStateRuntime.climbRope(entry);
            int ropeX = climbRope != null ? climbRope.x() : botPos.x;
            int ropeRegionId = graph.findRopeRegionId(new Point(ropeX, botPos.y));
            if (ropeRegionId >= 0) {
                return ropeRegionId;
            }
        }
        if (AgentBotMovementStateRuntime.inAir(entry)) {
            // Airborne points do not have a meaningful "current region". A ground lookup from an
            // in-flight point resolves to whatever foothold is below the arc, which can be an
            // unrelated upper platform. That makes runtime navigation discard the committed jump
            // edge even though the authored graph and ballistic landing simulation still agree.
            return -1;
        }
        return graph.findRegionId(map, botPos);
    }

    public static int resolveTargetRegionId(AgentNavigationGraph graph,
                                     BotEntry entry,
                                     MapleMap map,
                                     Point targetPos) {
        if (targetPos == null) {
            return -1;
        }

        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        List<BotEntry> siblingEntries = owner == null
                ? List.of()
                : AgentBotSessionLifecycleSideEffects.getBotEntries(owner.getId());
        Character followAnchor = AgentFollowAnchorService.resolve(entry, owner, siblingEntries);
        if (AgentBotModeStateRuntime.following(entry)
                && !AgentBotMoveTargetStateRuntime.hasMoveTarget(entry)
                && !AgentBotFarmAnchorStateRuntime.hasFarmAnchor(entry)
                && !AgentBotShopStateRuntime.shopVisitPending(entry)
                && !AgentBotModeStateRuntime.grinding(entry)
                && followAnchor != null
                && followAnchor.getMap() == map) {
            // Follow mode + owner climbing: prioritise a rope target. The follow
            // resolver may have already snapped targetPos to a rope's X, so the
            // exact equality check below would miss — explicitly look for a rope
            // at targetPos, and fall back to the follow anchor's own rope region if none
            // is found there. This keeps the bot climbing onto rope alongside
            // the anchor instead of clamping to the platform below the rope.
            if (CharacterStance.isClimbing(followAnchor.getStance())) {
                int ropeRegionId = graph.findRopeRegionId(targetPos);
                if (ropeRegionId >= 0) {
                    return ropeRegionId;
                }
                return resolveCharacterRegionId(graph, map, followAnchor);
            }
            if (targetPos.equals(followAnchor.getPosition())) {
                return resolveCharacterRegionId(graph, map, followAnchor);
            }
        }

        return resolvePointTargetRegionId(graph, map, targetPos);
    }

    public static int resolveCharacterRegionId(AgentNavigationGraph graph,
                                               MapleMap map,
                                               Character character) {
        if (character == null) {
            return -1;
        }

        Point position = character.getPosition();
        if (position == null) {
            return -1;
        }

        if (CharacterStance.isClimbing(character.getStance())) {
            int ropeRegionId = graph.findRopeRegionId(position);
            if (ropeRegionId >= 0) {
                return ropeRegionId;
            }
        }

        return resolvePointTargetRegionId(graph, map, position);
    }

    public static int resolvePointTargetRegionId(AgentNavigationGraph graph,
                                                 MapleMap map,
                                                 Point position) {
        int ropeRegionId = graph.findRopeRegionId(position);
        if (ropeRegionId >= 0 && shouldPreferRopeRegion(map, position)) {
            return ropeRegionId;
        }
        return graph.findRegionId(map, position);
    }

    private static boolean shouldPreferRopeRegion(MapleMap map, Point position) {
        return BotPhysicsEngine.isGroundFarBelow(map, position);
    }

    private static boolean isRopeEntryEdge(AgentNavigationGraph graph, AgentNavigationGraph.Edge edge) {
        if (edge.type != AgentNavigationGraph.EdgeType.CLIMB) {
            return false;
        }

        AgentNavigationGraph.Region from = graph.getRegion(edge.fromRegionId);
        AgentNavigationGraph.Region to = graph.getRegion(edge.toRegionId);
        return from != null && to != null && !from.isRopeRegion && to.isRopeRegion;
    }

    static boolean isTopStepOffExit(Rope rope, Point botPos, AgentNavigationGraph.Edge edge) {
        if (rope == null || botPos == null || edge == null || edge.launchStepX != 0) {
            return false;
        }
        return edge.startPoint.y == rope.topY()
                && Math.abs(edge.endPoint.y - rope.topY()) <= AgentMovementPhysicsConfig.configuredJumpYThreshold() * 2
                && botPos.y <= rope.topY() + AgentMovementPhysicsConfig.configuredJumpYThreshold() * 2;
    }

    private static Rope findRopeForRegion(MapleMap map, AgentNavigationGraph.Region region) {
        return AgentNavigationGraphService.findRopeFromRegion(map, region);
    }

}
