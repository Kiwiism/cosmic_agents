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
    private static final int MAX_TRANSITIONS = config.AgentTuning.intValue(
            "server.agents.capabilities.navigation.AgentNavigationProgressState.MAX_TRANSITIONS");

    private int mapId = Integer.MIN_VALUE;
    private Point target;
    private int observedRegionId = -1;
    private int detectedCycleLength;
    private final ArrayDeque<AgentNavigationTraceSnapshot.Transition> transitions =
            new ArrayDeque<>();
    private long lastProgressAtMs;
    private String loopKind = "";

    synchronized void observe(int currentMapId, Point currentTarget, int currentRegionId, long nowMs) {
        if (!sameScope(currentMapId, currentTarget)) {
            reset(currentMapId, currentTarget, currentRegionId, nowMs);
            return;
        }
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

    synchronized Snapshot snapshot(long nowMs) {
        return new Snapshot(observedRegionId, lastProgressAtMs, loopKind,
                null, 0L, List.copyOf(transitions));
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
        transitions.clear();
        lastProgressAtMs = nowMs;
        loopKind = "";
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
                    List<AgentNavigationTraceSnapshot.Transition> transitions) {
    }

}
