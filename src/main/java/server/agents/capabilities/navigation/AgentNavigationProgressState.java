package server.agents.capabilities.navigation;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Detects target-scoped route cycles that still produce physical movement. */
final class AgentNavigationProgressState {
    static final AgentCapabilityStateKey<AgentNavigationProgressState> STATE_KEY =
            new AgentCapabilityStateKey<>(
                    "navigation.progress",
                    AgentNavigationProgressState.class,
                    AgentNavigationProgressState::new);

    private static final int TARGET_SCOPE_RADIUS_PX = config.AgentTuning.intValue(
            "server.agents.capabilities.navigation.AgentNavigationProgressState.TARGET_SCOPE_RADIUS_PX");
    private static final long EDGE_SUPPRESSION_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.navigation.AgentNavigationProgressState.EDGE_SUPPRESSION_MS");
    private static final int MAX_TRANSITIONS = 24;

    private int mapId = Integer.MIN_VALUE;
    private Point target;
    private int observedRegionId = -1;
    private int previousFromRegionId = -1;
    private int previousToRegionId = -1;
    private int lastFromRegionId = -1;
    private int lastToRegionId = -1;
    private EdgeSignature suppressedEdge;
    private long suppressedUntilMs;
    private boolean recoveryPending;
    private final ArrayDeque<AgentNavigationTraceSnapshot.Transition> transitions =
            new ArrayDeque<>();
    private long lastProgressAtMs;
    private String loopKind = "";

    synchronized void observe(int currentMapId, Point currentTarget, int currentRegionId, long nowMs) {
        if (!sameScope(currentMapId, currentTarget)) {
            reset(currentMapId, currentTarget, currentRegionId, nowMs);
            return;
        }
        expireSuppression(nowMs);
        if (currentRegionId < 0) {
            return;
        }
        if (observedRegionId < 0) {
            observedRegionId = currentRegionId;
            return;
        }
        if (observedRegionId == currentRegionId) {
            return;
        }

        previousFromRegionId = lastFromRegionId;
        previousToRegionId = lastToRegionId;
        lastFromRegionId = observedRegionId;
        lastToRegionId = currentRegionId;
        observedRegionId = currentRegionId;
        transitions.addLast(new AgentNavigationTraceSnapshot.Transition(
                lastFromRegionId, lastToRegionId, nowMs));
        while (transitions.size() > MAX_TRANSITIONS) {
            transitions.removeFirst();
        }
        lastProgressAtMs = nowMs;
        loopKind = detectLoop();
    }

    synchronized boolean suppressIfAlternatingCycle(AgentNavigationGraph.Edge candidate, long nowMs) {
        expireSuppression(nowMs);
        if (candidate == null
                || previousFromRegionId != candidate.fromRegionId
                || previousToRegionId != candidate.toRegionId
                || lastFromRegionId != candidate.toRegionId
                || lastToRegionId != candidate.fromRegionId) {
            return false;
        }
        suppress(candidate, nowMs);
        return true;
    }

    synchronized void suppress(AgentNavigationGraph.Edge edge, long nowMs) {
        if (edge == null) {
            return;
        }
        suppressedEdge = EdgeSignature.of(edge);
        suppressedUntilMs = nowMs + Math.max(1L, EDGE_SUPPRESSION_MS);
        recoveryPending = true;
        if (loopKind.isBlank()) {
            loopKind = "edge-oscillation";
        }
    }

    synchronized boolean allows(AgentNavigationGraph.Edge edge, long nowMs) {
        expireSuppression(nowMs);
        return suppressedEdge == null || !suppressedEdge.matches(edge);
    }

    synchronized boolean consumeRecoveryPending() {
        boolean pending = recoveryPending;
        recoveryPending = false;
        return pending;
    }

    synchronized void clearRecoveryPending() {
        recoveryPending = false;
    }

    synchronized int recoveryDirection(Point currentPosition, Point targetPosition) {
        if (currentPosition == null) {
            return 0;
        }
        if (suppressedEdge != null && suppressedEdge.startPoint.x != currentPosition.x) {
            return suppressedEdge.startPoint.x > currentPosition.x ? -1 : 1;
        }
        if (targetPosition != null && targetPosition.x != currentPosition.x) {
            return targetPosition.x > currentPosition.x ? 1 : -1;
        }
        return 1;
    }

    synchronized Snapshot snapshot(long nowMs) {
        expireSuppression(nowMs);
        return new Snapshot(observedRegionId, lastProgressAtMs, loopKind,
                suppressedEdge == null ? null : suppressedEdge.snapshot(),
                suppressedUntilMs, recoveryPending, List.copyOf(transitions));
    }

    private boolean sameScope(int currentMapId, Point currentTarget) {
        if (mapId != currentMapId || target == null || currentTarget == null) {
            return target == null && currentTarget == null && mapId == currentMapId;
        }
        return target.distanceSq(currentTarget)
                <= (long) TARGET_SCOPE_RADIUS_PX * TARGET_SCOPE_RADIUS_PX;
    }

    private void reset(int currentMapId, Point currentTarget, int currentRegionId, long nowMs) {
        mapId = currentMapId;
        target = currentTarget == null ? null : new Point(currentTarget);
        observedRegionId = currentRegionId;
        previousFromRegionId = -1;
        previousToRegionId = -1;
        lastFromRegionId = -1;
        lastToRegionId = -1;
        suppressedEdge = null;
        suppressedUntilMs = 0L;
        recoveryPending = false;
        transitions.clear();
        lastProgressAtMs = nowMs;
        loopKind = "";
    }

    private void expireSuppression(long nowMs) {
        if (suppressedEdge != null && nowMs >= suppressedUntilMs) {
            suppressedEdge = null;
            suppressedUntilMs = 0L;
        }
    }

    private String detectLoop() {
        List<AgentNavigationTraceSnapshot.Transition> recent = new ArrayList<>(transitions);
        if (recent.size() < 3) {
            return "";
        }
        List<Integer> regions = new ArrayList<>();
        regions.add(recent.getFirst().fromRegionId());
        recent.forEach(transition -> regions.add(transition.toRegionId()));
        for (int cycleLength = 2; cycleLength <= 4; cycleLength++) {
            int required = cycleLength * 2;
            if (regions.size() < required) {
                continue;
            }
            int start = regions.size() - required;
            boolean repeated = true;
            for (int index = 0; index < cycleLength; index++) {
                if (!regions.get(start + index).equals(
                        regions.get(start + cycleLength + index))) {
                    repeated = false;
                    break;
                }
            }
            if (repeated) {
                return cycleLength == 2 ? "A/B oscillation" : cycleLength + "-region cycle";
            }
        }
        return "";
    }

    record Snapshot(int currentRegionId,
                    long lastProgressAtMs,
                    String loopKind,
                    AgentNavigationTraceSnapshot.Edge suppressedEdge,
                    long suppressedUntilMs,
                    boolean recoveryPending,
                    List<AgentNavigationTraceSnapshot.Transition> transitions) {
    }

    private record EdgeSignature(int fromRegionId,
                                 int toRegionId,
                                 AgentNavigationGraph.EdgeType type,
                                 Point startPoint,
                                 Point endPoint,
                                 int portalId) {
        static EdgeSignature of(AgentNavigationGraph.Edge edge) {
            return new EdgeSignature(edge.fromRegionId, edge.toRegionId, edge.type,
                    new Point(edge.startPoint), new Point(edge.endPoint), edge.portalId);
        }

        AgentNavigationTraceSnapshot.Edge snapshot() {
            return new AgentNavigationTraceSnapshot.Edge(
                    fromRegionId, toRegionId, type,
                    startPoint.x, startPoint.y, endPoint.x, endPoint.y,
                    startPoint.x, startPoint.x, 0, portalId,
                    0, 0, 0, 0);
        }

        boolean matches(AgentNavigationGraph.Edge edge) {
            return edge != null
                    && fromRegionId == edge.fromRegionId
                    && toRegionId == edge.toRegionId
                    && type == edge.type
                    && portalId == edge.portalId
                    && startPoint.equals(edge.startPoint)
                    && endPoint.equals(edge.endPoint);
        }
    }
}
