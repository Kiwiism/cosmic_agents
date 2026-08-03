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
    private static final int MAX_TRANSITIONS = config.AgentTuning.intValue(
            "server.agents.capabilities.navigation.AgentNavigationProgressState.MAX_TRANSITIONS");

    private int mapId = Integer.MIN_VALUE;
    private Point target;
    private int observedRegionId = -1;
    private int detectedCycleLength;
    private EdgeSignature suppressedEdge;
    private long suppressedUntilMs;
    private boolean recoveryPending;
    private final ArrayDeque<AgentNavigationTraceSnapshot.Transition> transitions =
            new ArrayDeque<>();
    private long lastProgressAtMs;
    private String loopKind = "";
    private EdgeSignature partialEdge;
    private int partialSelections;

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

        int fromRegionId = observedRegionId;
        observedRegionId = currentRegionId;
        transitions.addLast(new AgentNavigationTraceSnapshot.Transition(
                fromRegionId, currentRegionId, nowMs));
        while (transitions.size() > MAX_TRANSITIONS) {
            transitions.removeFirst();
        }
        lastProgressAtMs = nowMs;
        LoopDetection loop = detectLoop();
        loopKind = loop.kind();
        detectedCycleLength = loop.length();
    }

    synchronized boolean suppressIfAlternatingCycle(AgentNavigationGraph.Edge candidate, long nowMs) {
        return suppressIfRepeatedCycle(candidate, nowMs);
    }

    synchronized boolean suppressIfRepeatedCycle(AgentNavigationGraph.Edge candidate, long nowMs) {
        expireSuppression(nowMs);
        if (candidate == null || detectedCycleLength < 2
                || transitions.size() < detectedCycleLength) {
            return false;
        }
        List<AgentNavigationTraceSnapshot.Transition> recent = new ArrayList<>(transitions);
        int start = recent.size() - detectedCycleLength;
        boolean repeatsCycleEdge = recent.subList(start, recent.size()).stream()
                .anyMatch(transition -> transition.fromRegionId() == candidate.fromRegionId
                        && transition.toRegionId() == candidate.toRegionId);
        if (!repeatsCycleEdge) {
            return false;
        }
        suppress(candidate, nowMs);
        return true;
    }

    synchronized boolean allowsPartialReuse(AgentNavigationGraph.Edge edge, long nowMs) {
        expireSuppression(nowMs);
        if (edge == null) {
            return false;
        }
        EdgeSignature signature = EdgeSignature.of(edge);
        if (!signature.equals(partialEdge)) {
            partialEdge = signature;
            partialSelections = 1;
            return true;
        }
        partialSelections++;
        if (partialSelections <= 2) {
            return true;
        }
        suppress(edge, nowMs);
        loopKind = "partial-route reuse exhausted";
        return false;
    }

    synchronized void clearPartialReuse() {
        partialEdge = null;
        partialSelections = 0;
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
        detectedCycleLength = 0;
        suppressedEdge = null;
        suppressedUntilMs = 0L;
        recoveryPending = false;
        transitions.clear();
        lastProgressAtMs = nowMs;
        loopKind = "";
        clearPartialReuse();
    }

    private void expireSuppression(long nowMs) {
        if (suppressedEdge != null && nowMs >= suppressedUntilMs) {
            suppressedEdge = null;
            suppressedUntilMs = 0L;
        }
    }

    private LoopDetection detectLoop() {
        List<AgentNavigationTraceSnapshot.Transition> recent = new ArrayList<>(transitions);
        if (recent.size() < 2) {
            return LoopDetection.NONE;
        }
        List<Integer> regions = new ArrayList<>();
        regions.add(recent.getFirst().fromRegionId());
        recent.forEach(transition -> regions.add(transition.toRegionId()));
        for (int cycleLength = 2; cycleLength <= 4; cycleLength++) {
            int required = cycleLength + 1;
            if (regions.size() < required) {
                continue;
            }
            int cycleStart = regions.size() - required;
            if (regions.get(cycleStart).equals(regions.getLast())) {
                return new LoopDetection(
                        cycleLength == 2 ? "A/B oscillation" : cycleLength + "-region cycle",
                        cycleLength);
            }
        }
        return LoopDetection.NONE;
    }

    private record LoopDetection(String kind, int length) {
        private static final LoopDetection NONE = new LoopDetection("", 0);
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
