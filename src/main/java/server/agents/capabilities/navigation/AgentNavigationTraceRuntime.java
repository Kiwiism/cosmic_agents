package server.agents.capabilities.navigation;

import client.Character;
import server.agents.capabilities.movement.AgentMovementStuckStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.progression.events.AgentProgressionEventPublisher;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.List;

/** Records navigation decisions and projects them as observer-safe snapshots. */
public final class AgentNavigationTraceRuntime {
    private AgentNavigationTraceRuntime() {
    }

    static void planned(AgentRuntimeEntry entry,
                        AgentNavigationGraph graph,
                        int currentRegionId,
                        int targetRegionId,
                        Point targetPosition,
                        AgentNavigationPathService.MovementPathSelection selection,
                        String source,
                        String reason,
                        long nowMs) {
        entry.navigationRouteTraceState().planned(graph, currentRegionId,
                targetRegionId, targetPosition, selection, source, reason, nowMs);
    }

    static void rejected(AgentRuntimeEntry entry,
                         AgentNavigationGraph graph,
                         int currentRegionId,
                         int targetRegionId,
                         Point targetPosition,
                         String source,
                         String reason,
                         long nowMs) {
        entry.navigationRouteTraceState().rejected(graph, currentRegionId,
                targetRegionId, targetPosition, source, reason, nowMs);
    }

    public static void recovered(AgentRuntimeEntry entry, String recoveryType, long nowMs) {
        if (entry != null) {
            entry.navigationRouteTraceState().recovered(recoveryType, nowMs);
        }
    }

    public static AgentNavigationTraceSnapshot snapshot(AgentRuntimeEntry entry, long nowMs) {
        if (entry == null || !AgentRuntimeIdentityRuntime.hasBot(entry)) {
            return null;
        }
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        AgentNavigationRouteTraceState.Snapshot route =
                entry.navigationRouteTraceState().snapshot();
        AgentNavigationProgressState.Snapshot progress = entry.capabilityStates()
                .require(AgentNavigationProgressState.STATE_KEY)
                .snapshot(nowMs);
        AgentNavigationGraph.Edge active = entry.navigationEdgeState().activeEdge();
        Point agentPosition = agent.getPosition();
        Point waypoint = entry.navigationTargetState().position();
        Point target = entry.navigationEdgeState().plannedTargetPosition();
        if (target == null) {
            target = route.targetPosition();
        }

        return new AgentNavigationTraceSnapshot(
                agent.getId(), agent.getName(), agent.getMapId(), nowMs,
                route.graphVersion(), route.speed(), route.jump(), route.revision(),
                route.plannedAtMs(), route.source(), route.reason(),
                AgentProgressionEventPublisher.objectiveId(entry),
                position(agentPosition), progress.currentRegionId(),
                entry.navigationTargetState().regionId(), position(target),
                position(waypoint), entry.navigationTargetState().precise(),
                entry.navigationDebugState().lastDecision(),
                entry.navigationDebugState().lastEdgeBlockReason(), route.path(),
                activeEdgeIndex(route.path(), active, progress.currentRegionId()),
                route.cost(), route.expandedNodes(), route.elapsedMicroseconds(),
                route.reached(), route.bestEffort(), route.capped(),
                AgentMovementStuckStateRuntime.stuckMs(entry),
                AgentMovementStuckStateRuntime.unstuckCooldownMs(entry),
                progress.lastProgressAtMs(), progress.loopKind(),
                progress.suppressedEdge(), progress.suppressedUntilMs(),
                route.recoveryCount(), route.lastRecoveryAtMs(),
                route.lastRecoveryType(), verticalStage(entry.verticalTraversalState()),
                progress.transitions());
    }

    private static AgentNavigationTraceSnapshot.Position position(Point point) {
        return point == null
                ? AgentNavigationTraceSnapshot.Position.missing()
                : new AgentNavigationTraceSnapshot.Position(true, point.x, point.y);
    }

    private static int activeEdgeIndex(List<AgentNavigationTraceSnapshot.Edge> path,
                                       AgentNavigationGraph.Edge active,
                                       int currentRegionId) {
        if (active != null) {
            for (int index = 0; index < path.size(); index++) {
                if (path.get(index).matches(active)) {
                    return index;
                }
            }
            for (int index = 0; index < path.size(); index++) {
                AgentNavigationTraceSnapshot.Edge edge = path.get(index);
                if (edge.type() == active.type
                        && edge.toRegionId() == active.toRegionId) {
                    return index;
                }
            }
        }
        for (int index = 0; index < path.size(); index++) {
            if (path.get(index).fromRegionId() == currentRegionId) {
                return index;
            }
        }
        return -1;
    }

    private static String verticalStage(AgentVerticalTraversalState state) {
        if (state == null || !state.active()) {
            return "";
        }
        if (state.groundedExitObserved()) {
            return "grounded-exit";
        }
        if (state.ropeAttachmentObserved()) {
            return "climbing";
        }
        return "approach-rope";
    }
}
