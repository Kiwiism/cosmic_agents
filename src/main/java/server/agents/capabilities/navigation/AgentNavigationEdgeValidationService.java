package server.agents.capabilities.navigation;

import client.Character;
import server.agents.capabilities.movement.AgentClimbStateRuntime;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/** Single pre-execution validation boundary layered over existing readiness predicates. */
public final class AgentNavigationEdgeValidationService {
    private AgentNavigationEdgeValidationService() {
    }

    public static Result validate(AgentNavigationGraph graph,
                                  AgentRuntimeEntry entry,
                                  int mapId,
                                  int currentRegionId,
                                  Point position,
                                  AgentNavigationGraph.Edge edge,
                                  long nowMs) {
        return validate(graph, entry, null, mapId, currentRegionId, position, edge, nowMs);
    }

    public static Result validate(AgentNavigationGraph graph,
                                  AgentRuntimeEntry entry,
                                  Character agent,
                                  int mapId,
                                  int currentRegionId,
                                  Point position,
                                  AgentNavigationGraph.Edge edge,
                                  long nowMs) {
        if (edge == null || position == null) {
            return new Result(Status.REJECTED, "missing-edge-or-position");
        }
        if (AgentNavigationEdgeReliabilityRuntime.suppressed(entry, mapId, edge, nowMs)) {
            return new Result(Status.REJECTED, "edge-suppressed");
        }
        if (!AgentNavigationReliabilityConfig.edgeValidationEnabled()
                || !AgentNavigationEdgeReliabilityState.isRisky(edge)) {
            return new Result(Status.READY, "validation-disabled-or-non-risky");
        }
        boolean climbing = AgentClimbStateRuntime.climbing(entry);
        boolean airborne = AgentMovementStateRuntime.inAir(entry);
        if (currentRegionId != edge.fromRegionId) {
            return new Result(Status.REJECTED, "unexpected-source-region");
        }
        if (!validAnchors(graph, edge)) {
            return new Result(Status.REJECTED, "unreachable-edge-anchor");
        }
        if (AgentMovementStateRuntime.downJumpPending(entry)) {
            return edge.type == AgentNavigationGraph.EdgeType.DROP
                    ? new Result(Status.IN_PROGRESS, "drop-in-progress")
                    : new Result(Status.REJECTED, "incompatible-down-jump-state");
        }
        if (edge.type == AgentNavigationGraph.EdgeType.CLIMB) {
            boolean entryEdge = AgentNavigationRopeEdgeService.isRopeEntryEdge(graph, edge);
            if (entryEdge && climbing) {
                return new Result(Status.IN_PROGRESS, "rope-entry-attached");
            }
            if (!entryEdge && !climbing) {
                return airborne
                        ? new Result(Status.IN_PROGRESS, "climb-exit-airborne")
                        : new Result(Status.REJECTED, "climb-exit-not-climbing");
            }
            if (entryEdge && airborne) {
                return new Result(Status.APPROACH, "rope-entry-airborne");
            }
        } else if (climbing) {
            return new Result(Status.REJECTED, "ground-edge-while-climbing");
        } else if (airborne) {
            return new Result(Status.IN_PROGRESS, "airborne-traversal");
        }

        boolean ready = switch (edge.type) {
            case JUMP, FLASH_JUMP ->
                    AgentNavigationEdgeReadinessService.canExecuteJumpFromCurrentPosition(
                            graph, position, edge);
            case DROP -> AgentNavigationEdgeReadinessService.canExecuteDropFromCurrentPosition(
                    graph, position, edge);
            case CLIMB -> climbReady(graph, agent, climbing, position, edge);
            default -> AgentNavigationEdgeReadinessService.isReadyForEdge(position, edge);
        };
        return ready
                ? new Result(Status.READY, "ready")
                : new Result(Status.APPROACH, "approach-edge-anchor");
    }

    private static boolean climbReady(AgentNavigationGraph graph,
                                      Character agent,
                                      boolean climbing,
                                      Point position,
                                      AgentNavigationGraph.Edge edge) {
        if (agent == null || agent.getMap() == null) {
            return AgentNavigationEdgeReadinessService.isReadyForEdge(position, edge);
        }
        if (climbing) {
            return AgentNavigationRopeEdgeService.canExecuteClimbExitFromCurrentPosition(
                    graph, position, edge,
                    region -> AgentNavigationGraphService.findRopeFromRegion(
                            agent.getMap(), region));
        }
        return AgentNavigationRopeEdgeService.canExecuteClimbEntryFromCurrentPosition(
                position, edge, AgentNavigationGraphService.findRopeFromRegion(
                        agent.getMap(), graph.getRegion(edge.toRegionId)));
    }

    static boolean validAnchors(AgentNavigationGraph graph, AgentNavigationGraph.Edge edge) {
        AgentNavigationGraph.Region from = graph == null ? null : graph.getRegion(edge.fromRegionId);
        AgentNavigationGraph.Region to = graph == null ? null : graph.getRegion(edge.toRegionId);
        if (from == null || to == null) {
            return false;
        }
        int launchTolerance = Math.max(0, AgentNavigationReliabilityConfig.launchTolerancePx());
        int landingTolerance = Math.max(0, AgentNavigationReliabilityConfig.landingTolerancePx());
        int attachmentTolerance = Math.max(0, AgentNavigationReliabilityConfig.attachmentTolerancePx());
        if (!pointBelongsTo(from, edge.startPoint,
                from.isRopeRegion ? attachmentTolerance : launchTolerance)) {
            return false;
        }
        if (!pointBelongsTo(to, edge.endPoint,
                to.isRopeRegion ? attachmentTolerance : landingTolerance)) {
            return false;
        }
        if ((edge.type == AgentNavigationGraph.EdgeType.JUMP
                || edge.type == AgentNavigationGraph.EdgeType.FLASH_JUMP
                || edge.type == AgentNavigationGraph.EdgeType.DROP)
                && (edge.launchMaxX < from.minX - launchTolerance
                || edge.launchMinX > from.maxX + launchTolerance)) {
            return false;
        }
        if (edge.type != AgentNavigationGraph.EdgeType.CLIMB || edge.ropeX == 0) {
            return true;
        }
        boolean matchesSourceRope = from.isRopeRegion
                && Math.abs(edge.ropeX - from.minX) <= attachmentTolerance;
        boolean matchesTargetRope = to.isRopeRegion
                && Math.abs(edge.ropeX - to.minX) <= attachmentTolerance;
        return matchesSourceRope || matchesTargetRope;
    }

    private static boolean pointBelongsTo(AgentNavigationGraph.Region region,
                                          Point point,
                                          int tolerance) {
        if (region.isRopeRegion) {
            return Math.abs(point.x - region.minX) <= tolerance
                    && point.y >= region.minY - tolerance
                    && point.y <= region.maxY + tolerance;
        }
        if (point.x < region.minX - tolerance || point.x > region.maxX + tolerance) {
            return false;
        }
        Point surface = region.pointAt(point.x);
        return Math.abs(point.y - surface.y) <= tolerance;
    }

    public enum Status {
        READY,
        APPROACH,
        IN_PROGRESS,
        REJECTED
    }

    public record Result(Status status, String reason) {
        public boolean rejected() {
            return status == Status.REJECTED;
        }

        public boolean ready() {
            return status == Status.READY;
        }
    }
}
