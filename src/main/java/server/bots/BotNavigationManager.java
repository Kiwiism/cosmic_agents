package server.bots;

import client.Character;
import server.maps.Portal;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

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
        final int regionId;
        final int score;

        SearchNode(int regionId, int score) {
            this.regionId = regionId;
            this.score = score;
        }
    }

    static NavigationDirective resolveTarget(BotEntry entry, Point rawTargetPos, boolean runAiTick) {
        Character bot = entry.bot;
        if (bot.getMap().getFootholds() == null) {
            clearNavigation(entry);
            return new NavigationDirective(rawTargetPos, false);
        }

        BotNavigationGraph graph = BotNavigationGraphProvider.getGraph(bot.getMap());
        Point botPos = bot.getPosition();
        int startRegionId = graph.findRegionId(bot.getMap(), botPos);
        int targetRegionId = graph.findRegionId(bot.getMap(), rawTargetPos);

        BotNavigationGraph.Edge edge = reuseCommittedEdge(graph, entry, startRegionId, targetRegionId);
        if (edge == null && runAiTick && startRegionId >= 0 && targetRegionId >= 0 && startRegionId != targetRegionId) {
            edge = findNextEdge(graph, bot, startRegionId, targetRegionId, rawTargetPos);
            if (edge != null) {
                entry.navEdge = edge;
                entry.navTargetRegionId = targetRegionId;
            }
        }

        if (edge == null) {
            clearNavigation(entry);
            return new NavigationDirective(rawTargetPos, false);
        }

        NavigationDirective executionDirective = tryExecuteEdge(entry, bot, botPos, rawTargetPos, edge, runAiTick);
        if (executionDirective != null) {
            return executionDirective;
        }

        entry.navPreciseTarget = shouldUsePreciseTarget(entry, botPos, edge);
        entry.navTargetPos = selectWaypoint(entry, botPos, edge);
        return new NavigationDirective(new Point(entry.navTargetPos), false);
    }

    private static void clearNavigation(BotEntry entry) {
        entry.navEdge = null;
        entry.navTargetPos = null;
        entry.navTargetRegionId = -1;
        entry.navPreciseTarget = false;
    }

    private static BotNavigationGraph.Edge reuseCommittedEdge(BotNavigationGraph graph,
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
        if (startRegionId == edge.toRegionId && !entry.inAir && !entry.climbing) {
            return null;
        }
        if (startRegionId == edge.fromRegionId) {
            return edge;
        }
        if ((entry.inAir || entry.climbing) && (startRegionId < 0 || startRegionId != edge.toRegionId)) {
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
        if (!runAiTick || !isReadyForEdge(botPos, edge)) {
            return null;
        }

        return switch (edge.type) {
            case JUMP -> tryExecuteJump(entry, bot, rawTargetPos, edge);
            case DROP -> tryExecuteDrop(entry, bot, rawTargetPos, edge);
            case PORTAL -> tryExecutePortal(entry, bot, rawTargetPos, edge);
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

        entry.navPreciseTarget = false;
        entry.navTargetPos = new Point(edge.endPoint);
        entry.jumpCooldownMs = BotMovementManager.delayAfterCurrentTick(BotMovementManager.cfg.JUMP_COOLDOWN_MS);
        BotMovementManager.initiateJump(entry, bot, edge.launchStepX);
        return new NavigationDirective(rawTargetPos, true);
    }

    private static NavigationDirective tryExecuteDrop(BotEntry entry,
                                                      Character bot,
                                                      Point rawTargetPos,
                                                      BotNavigationGraph.Edge edge) {
        if (entry.inAir || entry.climbing || entry.downJumpPending || entry.jumpCooldownMs != 0) {
            return null;
        }

        entry.navPreciseTarget = false;
        entry.navTargetPos = new Point(edge.endPoint);
        entry.downJumpPending = true;
        bot.setStance(BotMovementManager.cfg.PRONE_STANCE);
        BotMovementManager.broadcastMovement(bot, 0, 0);
        return new NavigationDirective(rawTargetPos, true);
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
        if (entry.inAir || entry.climbing) {
            return false;
        }
        return switch (edge.type) {
            case WALK -> false;
            case JUMP, DROP, PORTAL, FLASH_JUMP, TELEPORT, CLIMB -> !isReadyForEdge(botPos, edge);
        };
    }

    private static Point selectWaypoint(BotEntry entry, Point botPos, BotNavigationGraph.Edge edge) {
        return switch (edge.type) {
            case WALK -> new Point(edge.endPoint);
            case JUMP, DROP, PORTAL, FLASH_JUMP, TELEPORT -> entry.inAir ? new Point(edge.endPoint) : new Point(edge.startPoint);
            case CLIMB -> entry.climbing || isReadyForEdge(botPos, edge)
                    ? new Point(edge.endPoint)
                    : new Point(edge.startPoint);
        };
    }

    private static BotNavigationGraph.Edge findNextEdge(BotNavigationGraph graph,
                                                        Character bot,
                                                        int startRegionId,
                                                        int targetRegionId,
                                                        Point targetPos) {
        List<BotNavigationGraph.Edge> path = findPath(graph, bot, startRegionId, targetRegionId, targetPos);
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
        PriorityQueue<SearchNode> open = new PriorityQueue<>(Comparator.comparingInt(node -> node.score));
        Map<Integer, Integer> gScore = new HashMap<>();
        Map<Integer, Integer> cameFrom = new HashMap<>();
        Map<Integer, BotNavigationGraph.Edge> cameByEdge = new HashMap<>();
        Set<Integer> closed = new HashSet<>();

        gScore.put(startRegionId, 0);
        open.add(new SearchNode(startRegionId, heuristic(graph.getRegion(startRegionId), targetPos)));

        while (!open.isEmpty()) {
            SearchNode current = open.poll();
            if (!closed.add(current.regionId)) {
                continue;
            }
            if (current.regionId == targetRegionId) {
                break;
            }

            int currentCost = gScore.getOrDefault(current.regionId, Integer.MAX_VALUE);
            for (BotNavigationGraph.Edge edge : graph.getOutgoing(current.regionId)) {
                if (!isEdgeUsable(graph, bot, edge)) {
                    continue;
                }

                int tentativeScore = currentCost + edge.cost;
                if (tentativeScore >= gScore.getOrDefault(edge.toRegionId, Integer.MAX_VALUE)) {
                    continue;
                }

                gScore.put(edge.toRegionId, tentativeScore);
                cameFrom.put(edge.toRegionId, current.regionId);
                cameByEdge.put(edge.toRegionId, edge);
                int fScore = tentativeScore + heuristic(graph.getRegion(edge.toRegionId), targetPos);
                open.add(new SearchNode(edge.toRegionId, fScore));
            }
        }

        return reconstructPath(startRegionId, targetRegionId, cameFrom, cameByEdge);
    }

    private static List<BotNavigationGraph.Edge> reconstructPath(int startRegionId,
                                                                 int targetRegionId,
                                                                 Map<Integer, Integer> cameFrom,
                                                                 Map<Integer, BotNavigationGraph.Edge> cameByEdge) {
        if (!cameByEdge.containsKey(targetRegionId)) {
            return List.of();
        }

        List<BotNavigationGraph.Edge> path = new ArrayList<>();
        int cursor = targetRegionId;
        while (cursor != startRegionId) {
            BotNavigationGraph.Edge edge = cameByEdge.get(cursor);
            if (edge == null) {
                return List.of();
            }

            path.add(0, edge);
            Integer previousRegionId = cameFrom.get(cursor);
            if (previousRegionId == null) {
                return List.of();
            }
            cursor = previousRegionId;
        }
        return path;
    }

    private static BotNavigationGraph.Edge collapseLeadingWalkEdges(List<BotNavigationGraph.Edge> path) {
        BotNavigationGraph.Edge first = path.get(0);
        if (first.type != BotNavigationGraph.EdgeType.WALK) {
            return first;
        }

        BotNavigationGraph.Edge lastWalk = first;
        int totalCost = first.cost;
        for (int i = 1; i < path.size(); i++) {
            BotNavigationGraph.Edge edge = path.get(i);
            if (edge.type != BotNavigationGraph.EdgeType.WALK) {
                break;
            }
            lastWalk = edge;
            totalCost += edge.cost;
        }

        return new BotNavigationGraph.Edge(first.fromRegionId, lastWalk.toRegionId, BotNavigationGraph.EdgeType.WALK,
                first.startPoint, lastWalk.endPoint, 0, 0, totalCost);
    }

    private static boolean isEdgeUsable(BotNavigationGraph graph, Character bot, BotNavigationGraph.Edge edge) {
        return switch (edge.type) {
            case WALK, DROP, CLIMB -> true;
            case JUMP -> landsOnExpectedRegion(graph, bot, edge);
            case PORTAL -> {
                Portal portal = bot.getMap().getPortal(edge.portalId);
                yield portal != null && portal.getPortalStatus();
            }
            case FLASH_JUMP, TELEPORT -> false;
        };
    }

    private static boolean landsOnExpectedRegion(BotNavigationGraph graph, Character bot, BotNavigationGraph.Edge edge) {
        BotMovementManager.JumpLanding landing = BotMovementManager.simulateJumpLanding(bot.getMap(), edge.startPoint, edge.launchStepX);
        if (landing == null) {
            return false;
        }

        int landingRegionId = graph.regionIdByFootholdId.getOrDefault(landing.foothold().getId(), -1);
        return landingRegionId == edge.toRegionId;
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

    private static int heuristic(BotNavigationGraph.Region region, Point targetPos) {
        Point anchor = region.pointAt(targetPos.x);
        return Math.abs(targetPos.x - anchor.x) + Math.abs(targetPos.y - anchor.y);
    }
}
