package server.bots;

import client.Character;
import server.maps.MapleMap;
import server.maps.Portal;
import server.maps.Rope;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

final class BotNavigationManager {
    static final class NavigationDirective {
        final Point targetPos;
        final boolean consumedTick;

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

    private record SearchState(int regionId, Point point) {
    }

    static NavigationDirective resolveTarget(BotEntry entry, Point rawTargetPos, boolean runAiTick) {
        long startedAt = System.nanoTime();
        try {
            Character bot = entry.bot;
            if (bot.getMap().getFootholds() == null) {
                clearNavigation(entry);
                return new NavigationDirective(rawTargetPos, false);
            }

            BotNavigationGraph graph = BotNavigationGraphProvider.getGraph(bot.getMap());
            Point botPos = bot.getPosition();
            int startRegionId = resolveCurrentRegionId(graph, entry, bot.getMap(), botPos);
            int targetRegionId = resolveTargetRegionId(graph, entry, bot.getMap(), rawTargetPos);

            BotNavigationGraph.Edge edge = reuseCommittedEdge(graph, entry, startRegionId, targetRegionId);
            boolean edgeReused = (edge != null);
            if (edge == null && runAiTick && startRegionId >= 0 && targetRegionId >= 0 && startRegionId != targetRegionId) {
                edge = findNextEdge(graph, bot, startRegionId, targetRegionId, rawTargetPos);
                if (edge != null) {
                    entry.navEdge = edge;
                    entry.navTargetRegionId = targetRegionId;
                }
            }

            if (edge == null) {
                entry.lastNavDecision = !runAiTick ? "no-ai"
                        : startRegionId < 0 || targetRegionId < 0 ? "no-region"
                        : startRegionId == targetRegionId ? "same-region" : "no-path";
                clearNavigation(entry);
                return new NavigationDirective(rawTargetPos, false);
            }

            NavigationDirective executionDirective = tryExecuteEdge(entry, bot, botPos, rawTargetPos, edge, runAiTick);
            if (executionDirective != null) {
                entry.lastNavDecision = "exec";
                if (entry.pathLogger != null) entry.pathLogger.record(entry, rawTargetPos, startRegionId, true);
                return executionDirective;
            }

            entry.lastNavDecision = edgeReused ? "reuse" : "new";
            entry.navPreciseTarget = shouldUsePreciseTarget(entry, botPos, edge);
            entry.navTargetPos = selectWaypoint(entry, botPos, edge);
            if (entry.pathLogger != null) entry.pathLogger.record(entry, rawTargetPos, startRegionId, false);
            return new NavigationDirective(new Point(entry.navTargetPos), false);
        } finally {
            BotPerformanceMonitor.record("nav-resolve", System.nanoTime() - startedAt);
        }
    }

    private static void clearNavigation(BotEntry entry) {
        entry.navEdge = null;
        entry.navTargetPos = null;
        entry.navTargetRegionId = -1;
        entry.navPreciseTarget = false;
    }

    static BotNavigationGraph.Edge reuseCommittedEdge(BotNavigationGraph graph,
                                                      BotEntry entry,
                                                      int startRegionId,
                                                      int targetRegionId) {
        BotNavigationGraph.Edge edge = entry.navEdge;
        if (edge == null) {
            return null;
        }
        if (targetRegionId < 0 || entry.navTargetRegionId != targetRegionId) {
            return null;
        }
        if (!isEdgeUsable(graph, entry.bot, edge)) {
            return null;
        }
        if (entry.climbing && isRopeEntryEdge(graph, edge)) {
            return null;
        }
        if (startRegionId == edge.toRegionId && !entry.inAir && !entry.climbing) {
            return null;
        }
        if (startRegionId == edge.fromRegionId) {
            return edge;
        }
        // While climbing, always keep the edge — findGroundFoothold gives false positives
        // (returns the platform below/behind the rope as the "current" region), which would
        // otherwise drop the exit edge the moment the bot enters the destination region's Y range.
        if (entry.inAir && (startRegionId < 0 || startRegionId != edge.toRegionId)) {
            return edge;
        }
        // DROP/JUMP arcs may enter the destination region before the bot touches down.
        // Keep the edge until landing so the bot doesn't replan mid-arc.
        if (entry.inAir && (edge.type == BotNavigationGraph.EdgeType.DROP
                || edge.type == BotNavigationGraph.EdgeType.JUMP)) {
            return edge;
        }
        // Collapsed WALK edges bridge multiple regions (e.g. r358→r359→r355 collapsed to r358→r355).
        // Keep the edge while the bot traverses any intermediate region — only drop once it arrives.
        if (edge.type == BotNavigationGraph.EdgeType.WALK && startRegionId >= 0) {
            return edge;
        }
        return null;
    }

    private static NavigationDirective tryExecuteEdge(BotEntry entry,
                                                      Character bot,
                                                      Point botPos,
                                                      Point rawTargetPos,
                                                      BotNavigationGraph.Edge edge,
                                                      boolean runAiTick) {
        if (!runAiTick) {
            return null;
        }

        return switch (edge.type) {
            case JUMP -> tryExecuteJump(entry, bot, rawTargetPos, edge);
            case DROP -> tryExecuteDrop(entry, bot, botPos, rawTargetPos, edge);
            case CLIMB -> tryExecuteClimb(entry, bot, botPos, rawTargetPos, edge);
            case PORTAL -> isReadyForEdge(botPos, edge) ? tryExecutePortal(entry, bot, rawTargetPos, edge) : null;
            default -> null;
        };
    }

    private static NavigationDirective tryExecuteJump(BotEntry entry,
                                                      Character bot,
                                                      Point rawTargetPos,
                                                      BotNavigationGraph.Edge edge) {
        if (entry.inAir || entry.climbing || entry.jumpCooldownMs != 0) {
            return null;
        }
        BotNavigationGraph graph = BotNavigationGraphProvider.getGraph(bot.getMap());
        Point botPos = bot.getPosition();
        if (!canExecuteJumpFromCurrentPosition(graph, bot.getMap(), botPos, edge)) {
            // Bot may be standing at the top of a rope region whose bottom is the jump entry.
            // Grab the rope and descend — tickClimbing will naturally drive toward edge.startPoint.
            if (edge.startPoint.y > botPos.y) {
                BotNavigationGraph.Region fromRegion = graph.getRegion(edge.fromRegionId);
                if (fromRegion != null && fromRegion.isRopeRegion) {
                    Rope rope = findRopeForRegion(bot.getMap(), fromRegion);
                    if (rope != null && canGrabRopeAtCurrentPosition(botPos, rope)) {
                        startClimbing(entry, bot, rope, edge.startPoint.y);
                        return new NavigationDirective(rawTargetPos, true);
                    }
                }
            }
            entry.lastEdgeBlockReason = "jump-pos";
            return null;
        }

        // Pre-launch wall check: mirrors tickAirborne's wall detection.
        // If the first horizontal step is immediately blocked, the bot would bounce
        // vertically in place forever — don't fire, let it walk to the actual start point.
        if (edge.launchStepX != 0) {
            int nextX = botPos.x + edge.launchStepX;
            if (BotPhysicsEngine.hasAirWallCollision(bot.getMap(), botPos, new Point(nextX, botPos.y))) {
                entry.lastEdgeBlockReason = "jump-wall";
                return null;
            }
        }

        entry.lastEdgeBlockReason = null;
        entry.navPreciseTarget = false;
        entry.navTargetPos = new Point(edge.endPoint);
        entry.jumpCooldownMs = BotMovementManager.delayAfterCurrentTick(BotMovementManager.cfg.JUMP_COOLDOWN_MS);
        BotMovementManager.initiateJump(entry, bot, edge.launchStepX);
        return new NavigationDirective(rawTargetPos, true);
    }

    private static NavigationDirective tryExecuteDrop(BotEntry entry,
                                                      Character bot,
                                                      Point botPos,
                                                      Point rawTargetPos,
                                                      BotNavigationGraph.Edge edge) {
        if (entry.inAir || entry.climbing || entry.downJumpPending || entry.jumpCooldownMs != 0) {
            return null;
        }

        BotNavigationGraph graph = BotNavigationGraphProvider.getGraph(bot.getMap());
        if (!canExecuteDropFromCurrentPosition(graph, bot.getMap(), botPos, edge)) {
            return null;
        }

        entry.navPreciseTarget = false;
        entry.navTargetPos = new Point(edge.endPoint);
        if (edge.launchStepX != 0) {
            BotPhysicsEngine.beginFall(entry, bot, edge.launchStepX);
            BotMovementManager.broadcastMovement(entry);
            return new NavigationDirective(rawTargetPos, true);
        }

        BotPhysicsEngine.queueDownJump(entry, bot);
        BotMovementManager.broadcastMovement(entry);
        return new NavigationDirective(rawTargetPos, true);
    }

    private static NavigationDirective tryExecuteClimb(BotEntry entry,
                                                       Character bot,
                                                       Point botPos,
                                                       Point rawTargetPos,
                                                       BotNavigationGraph.Edge edge) {
        if (entry.inAir || entry.downJumpPending) {
            return null;
        }

        if (entry.climbing) {
            return tryExecuteClimbExit(entry, bot, botPos, rawTargetPos, edge);
        } else {
            return tryExecuteClimbEntry(entry, bot, botPos, rawTargetPos, edge);
        }
    }

    private static NavigationDirective tryExecuteClimbEntry(BotEntry entry,
                                                             Character bot,
                                                             Point botPos,
                                                             Point rawTargetPos,
                                                             BotNavigationGraph.Edge edge) {
        BotNavigationGraph graph = BotNavigationGraphProvider.getGraph(bot.getMap());
        BotNavigationGraph.Region toRegion = graph.getRegion(edge.toRegionId);
        Rope rope = findRopeForRegion(bot.getMap(), toRegion);
        if (rope == null) {
            return null;
        }
        if (!canExecuteClimbEntryFromCurrentPosition(bot.getMap(), botPos, edge, rope)) {
            entry.lastEdgeBlockReason = "climb-pos";
            return null;
        }

        if (canGrabRopeAtCurrentPosition(botPos, rope)
                || canGrabRopeFromTopPlatform(edge, botPos, rope)) {
            entry.lastEdgeBlockReason = null;
            startClimbing(entry, bot, rope, edge.endPoint.y);
            return new NavigationDirective(rawTargetPos, true);
        }

        if (entry.jumpCooldownMs == 0 && BotMovementManager.canReachRopeFromGround(bot.getMap(), botPos, rope)) {
            entry.lastEdgeBlockReason = null;
            entry.jumpCooldownMs = BotMovementManager.delayAfterCurrentTick(BotMovementManager.cfg.JUMP_COOLDOWN_MS);
            BotMovementManager.initiateRopeJump(entry, bot, rope.x() - botPos.x);
            return new NavigationDirective(rawTargetPos, true);
        }

        entry.lastEdgeBlockReason = "climb-reach";
        return null;
    }

    private static NavigationDirective tryExecuteClimbExit(BotEntry entry,
                                                            Character bot,
                                                            Point botPos,
                                                            Point rawTargetPos,
                                                            BotNavigationGraph.Edge edge) {
        if (entry.jumpCooldownMs != 0) {
            return null;
        }

        BotNavigationGraph graph = BotNavigationGraphProvider.getGraph(bot.getMap());
        if (!canExecuteClimbExitFromCurrentPosition(graph, bot.getMap(), botPos, edge)) {
            return null;
        }
        BotNavigationGraph.Region toRegion = graph.getRegion(edge.toRegionId);

        if (toRegion != null && toRegion.isRopeRegion) {
            // Rope-to-rope: jump to the other rope
            Rope targetRope = findRopeForRegion(bot.getMap(), toRegion);
            if (targetRope == null || isSameRope(entry.climbRope, targetRope)) {
                return null;
            }
            BotMovementManager.jumpToRope(entry, bot, edge.launchStepX);
            return new NavigationDirective(rawTargetPos, true);
        }

        if (edge.launchStepX == 0) {
            if (isTopStepOffExit(entry.climbRope, botPos, edge)) {
                BotPhysicsEngine.landOnGround(entry, bot, edge.endPoint);
            } else {
                BotPhysicsEngine.beginFall(entry, bot, 0);
            }
            BotMovementManager.broadcastMovement(entry);
            return new NavigationDirective(rawTargetPos, true);
        }

        // Jump off rope
        BotMovementManager.jumpOffRope(entry, bot, edge.launchStepX);
        return new NavigationDirective(rawTargetPos, true);
    }

    static boolean canExecuteDropFromCurrentPosition(BotNavigationGraph graph,
                                                     MapleMap map,
                                                     Point botPos,
                                                     BotNavigationGraph.Edge edge) {
        if (edge.type != BotNavigationGraph.EdgeType.DROP) {
            return false;
        }

        // Walk-off drops (launchStepX != 0) must start from near the platform edge, not mid-platform.
        // This mirrors the 14px tolerance used by isReadyForEdge for DROP edges, ensuring the graph
        // builder's edge placement and the runtime execution check share the same positional constraint.
        if (edge.launchStepX != 0 && Math.abs(botPos.x - edge.startPoint.x) > 14) {
            return false;
        }

        BotPhysicsEngine.JumpLanding landing = edge.launchStepX == 0
                ? BotPhysicsEngine.simulateDownJumpLanding(map, botPos)
                : BotPhysicsEngine.simulateFallLanding(map, botPos, edge.launchStepX);
        if (landing == null) {
            return false;
        }

        int landingRegionId = graph.regionIdByFootholdId.getOrDefault(landing.foothold().getId(), -1);
        return landingRegionId == edge.toRegionId;
    }

    private static NavigationDirective tryExecutePortal(BotEntry entry,
                                                        Character bot,
                                                        Point rawTargetPos,
                                                        BotNavigationGraph.Edge edge) {
        if (!usePortal(bot, edge.portalId)) {
            return null;
        }

        clearNavigation(entry);
        BotMovementManager.resetEntryState(entry);
        return new NavigationDirective(rawTargetPos, true);
    }

    private static boolean shouldUsePreciseTarget(BotEntry entry, Point botPos, BotNavigationGraph.Edge edge) {
        if (entry.inAir) {
            return false;
        }
        BotNavigationGraph graph = BotNavigationGraphProvider.getGraph(entry.bot.getMap());
        return switch (edge.type) {
            case WALK -> shouldUsePreciseWalkTarget(edge);
            case JUMP -> !canExecuteJumpFromCurrentPosition(graph, entry.bot.getMap(), botPos, edge);
            case DROP -> !canExecuteDropFromCurrentPosition(graph, entry.bot.getMap(), botPos, edge);
            case CLIMB -> entry.climbing
                    ? !canExecuteClimbExitFromCurrentPosition(graph, entry.bot.getMap(), botPos, edge)
                    : !canExecuteClimbEntryFromCurrentPosition(entry.bot.getMap(), botPos, edge,
                    findRopeForRegion(entry.bot.getMap(), graph.getRegion(edge.toRegionId)));
            case PORTAL -> !isReadyForEdge(botPos, edge);
        };
    }

    private static Point selectWaypoint(BotEntry entry, Point botPos, BotNavigationGraph.Edge edge) {
        return switch (edge.type) {
            case WALK -> new Point(edge.endPoint);
            case CLIMB -> selectClimbWaypoint(entry, botPos, edge);
            case JUMP, DROP, PORTAL -> entry.inAir ? new Point(edge.endPoint) : new Point(edge.startPoint);
        };
    }

    static Point selectClimbWaypoint(BotEntry entry, Point botPos, BotNavigationGraph.Edge edge) {
        if (entry.inAir) {
            return new Point(edge.endPoint);
        }
        if (entry.climbing) {
            BotNavigationGraph graph = BotNavigationGraphProvider.getGraph(entry.bot.getMap());
            if (canExecuteClimbExitFromCurrentPosition(graph, entry.bot.getMap(), botPos, edge)) {
                return new Point(botPos);
            }
        }
        return new Point(edge.startPoint);
    }

    private static BotNavigationGraph.Edge findNextEdge(BotNavigationGraph graph,
                                                        Character bot,
                                                        int startRegionId,
                                                        int targetRegionId,
                                                        Point targetPos) {
        List<BotNavigationGraph.Edge> path = findPath(graph, bot.getMap(), bot.getPosition(), startRegionId, targetRegionId, targetPos);
        if (path.isEmpty()) {
            return null;
        }
        return collapseLeadingWalkEdges(path);
    }

    static List<BotNavigationGraph.Edge> findPath(BotNavigationGraph graph,
                                                  Character bot,
                                                  int startRegionId,
                                                  int targetRegionId,
                                                  Point targetPos) {
        return findPath(graph, bot.getMap(), bot.getPosition(), startRegionId, targetRegionId, targetPos);
    }

    static List<BotNavigationGraph.Edge> findPath(BotNavigationGraph graph,
                                                  MapleMap map,
                                                  Point startPos,
                                                  int startRegionId,
                                                  int targetRegionId,
                                                  Point targetPos) {
        long startedAt = System.nanoTime();
        try {
            PriorityQueue<SearchNode> open = new PriorityQueue<>(Comparator.comparingInt(node -> node.score));
            Map<SearchState, Integer> gScore = new HashMap<>();
            Map<SearchState, SearchState> cameFrom = new HashMap<>();
            Map<SearchState, BotNavigationGraph.Edge> cameByEdge = new HashMap<>();
            SearchState startState = new SearchState(startRegionId, new Point(startPos));
            SearchState bestGoalState = null;
            int bestGoalCost = Integer.MAX_VALUE;

            gScore.put(startState, 0);
            open.add(new SearchNode(startState, 0, heuristic(startPos, targetPos)));

            while (!open.isEmpty()) {
                SearchNode current = open.poll();
                if (current.cost != gScore.getOrDefault(current.state, Integer.MAX_VALUE)) {
                    continue;
                }

                if (current.state.regionId == targetRegionId) {
                    int goalCost = current.cost + intraRegionTravelCost(graph, current.state.regionId, current.state.point, targetPos);
                    if (goalCost < bestGoalCost) {
                        bestGoalCost = goalCost;
                        bestGoalState = current.state;
                    }
                }

                for (BotNavigationGraph.Edge edge : graph.getOutgoing(current.state.regionId)) {
                    if (!isEdgeUsable(graph, map, edge)) {
                        continue;
                    }

                    int tentativeCost = current.cost + intraRegionTravelCost(graph, current.state.regionId, current.state.point, edge.startPoint) + edge.cost;
                    SearchState nextState = new SearchState(edge.toRegionId, edge.endPoint);
                    if (tentativeCost >= gScore.getOrDefault(nextState, Integer.MAX_VALUE)) {
                        continue;
                    }

                    gScore.put(nextState, tentativeCost);
                    cameFrom.put(nextState, current.state);
                    cameByEdge.put(nextState, edge);
                    int fScore = tentativeCost + heuristic(edge.endPoint, targetPos);
                    open.add(new SearchNode(nextState, tentativeCost, fScore));
                }
            }

            return reconstructPath(startState, bestGoalState, cameFrom, cameByEdge);
        } finally {
            BotPerformanceMonitor.recordPathfind(System.nanoTime() - startedAt);
        }
    }

    private static List<BotNavigationGraph.Edge> reconstructPath(SearchState startState,
                                                                 SearchState goalState,
                                                                 Map<SearchState, SearchState> cameFrom,
                                                                 Map<SearchState, BotNavigationGraph.Edge> cameByEdge) {
        if (goalState == null || !cameByEdge.containsKey(goalState)) {
            return List.of();
        }

        List<BotNavigationGraph.Edge> path = new ArrayList<>();
        SearchState cursor = goalState;
        while (!cursor.equals(startState)) {
            BotNavigationGraph.Edge edge = cameByEdge.get(cursor);
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

    static BotNavigationGraph.Edge collapseLeadingWalkEdges(List<BotNavigationGraph.Edge> path) {
        BotNavigationGraph.Edge first = path.get(0);
        if (first.type != BotNavigationGraph.EdgeType.WALK) {
            return first;
        }

        BotNavigationGraph.Edge lastWalk = first;
        int totalCost = first.cost;
        int walkCount = 1;
        for (int i = 1; i < path.size(); i++) {
            BotNavigationGraph.Edge edge = path.get(i);
            if (edge.type != BotNavigationGraph.EdgeType.WALK) {
                break;
            }
            lastWalk = edge;
            totalCost += edge.cost;
            walkCount++;
        }

        if (!isNoMovementWalk(first.startPoint, lastWalk.endPoint)) {
            return new BotNavigationGraph.Edge(first.fromRegionId, lastWalk.toRegionId, BotNavigationGraph.EdgeType.WALK,
                first.startPoint, lastWalk.endPoint, 0, 0, 0, 0, 0, totalCost);
        }

        if (walkCount >= path.size()) {
            return null;
        }

        BotNavigationGraph.Edge next = path.get(walkCount);
        return new BotNavigationGraph.Edge(first.fromRegionId, next.toRegionId, next.type,
                next.startPoint, next.endPoint, next.launchStepX, next.portalId,
                next.ropeX, next.ropeTopY, next.ropeBottomY, totalCost + next.cost);
    }

    private static boolean isEdgeUsable(BotNavigationGraph graph, Character bot, BotNavigationGraph.Edge edge) {
        return isEdgeUsable(graph, bot.getMap(), edge);
    }

    private static boolean isEdgeUsable(BotNavigationGraph graph, MapleMap map, BotNavigationGraph.Edge edge) {
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

    private static boolean isReadyForEdge(Point botPos, BotNavigationGraph.Edge edge) {
        int dx = Math.abs(botPos.x - edge.startPoint.x);
        int dy = Math.abs(botPos.y - edge.startPoint.y);

        return switch (edge.type) {
            case JUMP -> dx <= 10 && dy <= BotMovementManager.cfg.JUMP_Y_THRESH;
            case DROP, CLIMB, PORTAL -> dx <= 14 && dy <= BotMovementManager.cfg.JUMP_Y_THRESH * 2;
            default -> dx <= BotMovementManager.cfg.STOP_DIST + 8
                    && dy <= BotMovementManager.cfg.JUMP_Y_THRESH * 2;
        };
    }

    static boolean canExecuteJumpFromCurrentPosition(BotNavigationGraph graph,
                                                     MapleMap map,
                                                     Point botPos,
                                                     BotNavigationGraph.Edge edge) {
        if (edge.type != BotNavigationGraph.EdgeType.JUMP) {
            return false;
        }

        BotPhysicsEngine.JumpLanding landing = BotPhysicsEngine.simulateJumpLanding(map, botPos, edge.launchStepX);
        if (landing != null) {
            int landingRegionId = graph.regionIdByFootholdId.getOrDefault(landing.foothold().getId(), -1);
            if (landingRegionId == edge.toRegionId) {
                return true;
            }
        }

        // Fallback: if the bot stopped slightly off the anchor (within isReadyForEdge JUMP tolerance),
        // try simulating from the edge's recorded start point instead. This avoids 1-4px positional
        // drift causing the simulated landing to miss the target foothold.
        // For vertical jumps (launchStepX==0) the tolerance is much tighter: horizontal position
        // is the sole determinant of landing foothold, so 9px offset causes an infinite bounce loop.
        int dx = Math.abs(botPos.x - edge.startPoint.x);
        int dy = Math.abs(botPos.y - edge.startPoint.y);
        if (isWithinJumpFallbackTolerance(edge, dx, dy)) {
            BotPhysicsEngine.JumpLanding anchorLanding = BotPhysicsEngine.simulateJumpLanding(map, edge.startPoint, edge.launchStepX);
            if (anchorLanding != null) {
                int regionId = graph.regionIdByFootholdId.getOrDefault(anchorLanding.foothold().getId(), -1);
                return regionId == edge.toRegionId;
            }
        }

        return false;
    }

    /** Exposed for testing. Vertical jumps use a tight X tolerance (3px) because landing
     *  foothold is determined purely by horizontal position; horizontal jumps use 10px. */
    static boolean isWithinJumpFallbackTolerance(BotNavigationGraph.Edge edge, int dx, int dy) {
        int xTol = edge.launchStepX == 0 ? 3 : 10;
        return dx <= xTol && dy <= BotMovementManager.cfg.JUMP_Y_THRESH;
    }

    private static int intraRegionTravelCost(Point from, Point to) {
        int travel = Math.abs(to.x - from.x) + Math.abs(to.y - from.y);
        return Math.max(0, (int) Math.round((travel * 1000.0) / Math.max(1, BotMovementManager.cfg.WALK_VEL)));
    }

    private static int intraRegionTravelCost(BotNavigationGraph graph, int regionId, Point from, Point to) {
        BotNavigationGraph.Region region = graph.getRegion(regionId);
        if (region != null && region.isRopeRegion) {
            int travel = Math.abs(to.y - from.y);
            return Math.max(0, (int) Math.round((travel * 1000.0) / Math.max(1, BotMovementManager.cfg.CLIMB_SPEED_PXS)));
        }
        return intraRegionTravelCost(from, to);
    }

    private static int heuristic(Point from, Point targetPos) {
        return intraRegionTravelCost(from, targetPos);
    }

    static boolean shouldUsePreciseWalkTarget(BotNavigationGraph.Edge edge) {
        return edge != null
                && edge.type == BotNavigationGraph.EdgeType.WALK
                && !isNoMovementWalk(edge.startPoint, edge.endPoint);
    }

    private static boolean isNoMovementWalk(Point start, Point end) {
        return Math.abs(end.x - start.x) <= 4 && Math.abs(end.y - start.y) <= 4;
    }

    private static boolean canGrabRopeAtCurrentPosition(Point botPos, Rope rope) {
        return Math.abs(botPos.x - rope.x()) <= BotMovementManager.cfg.ROPE_GRAB_X
                && botPos.y >= rope.topY()
                && botPos.y <= rope.bottomY();
    }

    private static boolean canGrabRopeFromTopPlatform(BotNavigationGraph.Edge edge, Point botPos, Rope rope) {
        return edge.startPoint.y <= rope.topY() + BotMovementManager.cfg.JUMP_Y_THRESH
                && Math.abs(botPos.x - rope.x()) <= BotMovementManager.cfg.ROPE_GRAB_X;
    }

    private static boolean canExecuteClimbEntryFromCurrentPosition(MapleMap map,
                                                                   Point botPos,
                                                                   BotNavigationGraph.Edge edge,
                                                                   Rope rope) {
        return rope != null && (canGrabRopeAtCurrentPosition(botPos, rope)
                || canGrabRopeFromTopPlatform(edge, botPos, rope)
                || BotMovementManager.canReachRopeFromGround(map, botPos, rope));
    }

    private static boolean canExecuteClimbExitFromCurrentPosition(BotNavigationGraph graph,
                                                                  MapleMap map,
                                                                  Point botPos,
                                                                  BotNavigationGraph.Edge edge) {
        if (edge.type != BotNavigationGraph.EdgeType.CLIMB) {
            return false;
        }

        BotNavigationGraph.Region toRegion = graph.getRegion(edge.toRegionId);
        if (toRegion != null && toRegion.isRopeRegion) {
            Rope rope = findRopeForRegion(map, toRegion);
            return BotPhysicsEngine.simulateRopeJumpGrab(map, botPos, edge.launchStepX, rope) != null;
        }

        if (edge.launchStepX == 0) {
            Point ground = BotPhysicsEngine.findGroundPoint(map, new Point(botPos.x, botPos.y - 3));
            if (ground == null || Math.abs(ground.y - botPos.y) > BotMovementManager.cfg.JUMP_Y_THRESH * 2) {
                return false;
            }

            return graph.findRegionId(map, ground) == edge.toRegionId;
        }

        BotPhysicsEngine.JumpLanding landing = BotPhysicsEngine.simulateRopeJumpLanding(map, botPos, edge.launchStepX);
        if (landing == null) {
            return false;
        }

        int landingRegionId = graph.regionIdByFootholdId.getOrDefault(landing.foothold().getId(), -1);
        return landingRegionId == edge.toRegionId;
    }

    private static void startClimbing(BotEntry entry, Character bot, Rope rope, int climbY) {
        BotPhysicsEngine.attachToRope(entry, bot, rope, climbY);
        BotMovementManager.broadcastMovement(entry);
    }

    static int resolveCurrentRegionId(BotNavigationGraph graph,
                                      BotEntry entry,
                                      MapleMap map,
                                      Point botPos) {
        if (entry.climbing && entry.climbRope != null) {
            // Rope climbing state is authoritative. Ground lookup below a rope often resolves to
            // the nearby platform instead of the rope region, which can replan from the wrong side
            // of the rope and bounce between entry/exit climb edges.
            int ropeRegionId = graph.findRopeRegionId(new Point(entry.climbRope.x(), botPos.y));
            if (ropeRegionId >= 0) {
                return ropeRegionId;
            }
        }
        return graph.findRegionId(map, botPos);
    }

    static int resolveTargetRegionId(BotNavigationGraph graph,
                                     BotEntry entry,
                                     MapleMap map,
                                     Point targetPos) {
        if (targetPos == null) {
            return -1;
        }

        Character owner = entry.owner;
        if (!entry.grinding && owner != null && owner.getMap() == map && targetPos.equals(owner.getPosition())) {
            return resolveCharacterRegionId(graph, map, owner);
        }

        return resolvePointTargetRegionId(graph, map, targetPos);
    }

    static int resolveCharacterRegionId(BotNavigationGraph graph,
                                        MapleMap map,
                                        Character character) {
        if (character == null) {
            return -1;
        }

        Point position = character.getPosition();
        if (position == null) {
            return -1;
        }

        if (isRopeOrLadderStance(character.getStance())) {
            int ropeRegionId = graph.findRopeRegionId(position);
            if (ropeRegionId >= 0) {
                return ropeRegionId;
            }
        }

        return resolvePointTargetRegionId(graph, map, position);
    }

    private static int resolvePointTargetRegionId(BotNavigationGraph graph,
                                                  MapleMap map,
                                                  Point position) {
        int ropeRegionId = graph.findRopeRegionId(position);
        if (ropeRegionId >= 0 && shouldPreferRopeRegion(map, position)) {
            return ropeRegionId;
        }
        return graph.findRegionId(map, position);
    }

    private static boolean shouldPreferRopeRegion(MapleMap map, Point position) {
        if (map == null || position == null) {
            return false;
        }

        Point ground = map.getPointBelow(position);
        return ground == null || ground.y > position.y + BotPhysicsEngine.cfg.MAX_SNAP_DROP;
    }

    private static boolean isRopeOrLadderStance(int stance) {
        return stance == BotPhysicsEngine.cfg.ROPE_STANCE || stance == BotPhysicsEngine.cfg.LADDER_STANCE;
    }

    private static boolean isRopeEntryEdge(BotNavigationGraph graph, BotNavigationGraph.Edge edge) {
        if (edge.type != BotNavigationGraph.EdgeType.CLIMB) {
            return false;
        }

        BotNavigationGraph.Region from = graph.getRegion(edge.fromRegionId);
        BotNavigationGraph.Region to = graph.getRegion(edge.toRegionId);
        return from != null && to != null && !from.isRopeRegion && to.isRopeRegion;
    }

    static boolean isTopStepOffExit(Rope rope, Point botPos, BotNavigationGraph.Edge edge) {
        if (rope == null || botPos == null || edge == null || edge.launchStepX != 0) {
            return false;
        }
        return edge.startPoint.y == rope.topY()
                && Math.abs(edge.endPoint.y - rope.topY()) <= BotMovementManager.cfg.JUMP_Y_THRESH * 2
                && botPos.y <= rope.topY() + BotMovementManager.cfg.JUMP_Y_THRESH * 2;
    }

    private static Rope findRopeForRegion(MapleMap map, BotNavigationGraph.Region region) {
        return BotNavigationGraphProvider.findRopeFromRegion(map, region);
    }

    private static boolean isSameRope(Rope left, Rope right) {
        return left != null && right != null
                && left.x() == right.x()
                && left.topY() == right.topY()
                && left.bottomY() == right.bottomY()
                && left.isLadder() == right.isLadder();
    }
}
