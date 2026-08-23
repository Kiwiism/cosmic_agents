package server.agents.capabilities.navigation;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, map-scoped reliability memory. It never mutates the shared graph. */
public final class AgentNavigationEdgeReliabilityState {
    public static final AgentCapabilityStateKey<AgentNavigationEdgeReliabilityState> STATE_KEY =
            new AgentCapabilityStateKey<>("navigation.edge-reliability",
                    AgentNavigationEdgeReliabilityState.class,
                    AgentNavigationEdgeReliabilityState::new);

    private int mapId = Integer.MIN_VALUE;
    private final LinkedHashMap<EdgeSignature, Failure> failures =
            new LinkedHashMap<>(16, 0.75f, true);
    private Attempt attempt;

    public synchronized void synchronizeMap(int currentMapId) {
        if (mapId != currentMapId) {
            mapId = currentMapId;
            failures.clear();
            attempt = null;
        }
    }

    public synchronized void recordFailure(int currentMapId,
                                           AgentNavigationGraph.Edge edge,
                                           long nowMs,
                                           int threshold,
                                           long suppressionMs,
                                           long retentionMs,
                                           int maxTrackedEdges) {
        if (!isRisky(edge)) {
            return;
        }
        synchronizeMap(currentMapId);
        expire(nowMs, retentionMs);
        EdgeSignature signature = EdgeSignature.of(edge);
        Failure previous = failures.get(signature);
        int count = previous == null ? 1 : previous.count + 1;
        boolean suppressionActive = previous != null && previous.suppressedUntilMs > nowMs;
        long suppressedUntil = count >= Math.max(1, threshold) && !suppressionActive
                ? nowMs + Math.max(1L, suppressionMs)
                : previous == null ? 0L : previous.suppressedUntilMs;
        failures.put(signature, new Failure(count, nowMs, suppressedUntil));
        trim(Math.max(1, maxTrackedEdges));
    }

    public synchronized void recordSuccess(int currentMapId, AgentNavigationGraph.Edge edge) {
        synchronizeMap(currentMapId);
        if (edge != null) {
            failures.remove(EdgeSignature.of(edge));
        }
        if (attempt != null && attempt.edge.matches(edge)) {
            attempt = null;
        }
    }

    public synchronized boolean isSuppressed(int currentMapId,
                                             AgentNavigationGraph.Edge edge,
                                             long nowMs,
                                             long retentionMs) {
        synchronizeMap(currentMapId);
        expire(nowMs, retentionMs);
        Failure failure = edge == null ? null : failures.get(EdgeSignature.of(edge));
        return failure != null && failure.suppressedUntilMs > nowMs;
    }

    public synchronized int penalty(int currentMapId,
                                    AgentNavigationGraph.Edge edge,
                                    long nowMs,
                                    long retentionMs,
                                    int perFailure,
                                    int maximum) {
        synchronizeMap(currentMapId);
        expire(nowMs, retentionMs);
        Failure failure = edge == null ? null : failures.get(EdgeSignature.of(edge));
        if (failure == null) {
            return 0;
        }
        long value = (long) Math.max(0, perFailure) * failure.count;
        return (int) Math.min(Math.max(0, maximum), value);
    }

    public synchronized void beginAttempt(int currentMapId,
                                          AgentNavigationGraph.Edge edge,
                                          int currentRegionId,
                                          Point position,
                                          long nowMs) {
        if (!isRisky(edge) || position == null) {
            return;
        }
        synchronizeMap(currentMapId);
        EdgeSignature signature = EdgeSignature.of(edge);
        if (attempt != null && attempt.edge.equals(signature)) {
            return;
        }
        attempt = new Attempt(signature, currentRegionId,
                position.distance(signature.endPoint()), nowMs);
    }

    /**
     * Keeps an active traversal attempt alive while an owning foreground route deliberately
     * spends a tick clearing a blocking monster. Combat is useful route progress even though
     * the character position does not change; charging that time to the structural edge made
     * the only usable jump or climb look unreliable and could suppress it for 30 seconds.
     */
    public synchronized void deferAttemptTimeout(int currentMapId, long nowMs) {
        synchronizeMap(currentMapId);
        if (attempt != null) {
            attempt = new Attempt(attempt.edge, attempt.startedRegionId,
                    attempt.bestDistanceToEndPx, nowMs);
        }
    }

    /** Returns the edge whose attempt timed out, or null while progress remains valid. */
    public synchronized AgentNavigationGraph.Edge observeAttempt(int currentMapId,
                                                                 int currentRegionId,
                                                                 Point position,
                                                                 long nowMs,
                                                                 long timeoutMs,
                                                                 int progressTolerancePx) {
        synchronizeMap(currentMapId);
        if (attempt == null || position == null) {
            return null;
        }
        if (attempt.startedRegionId != attempt.edge.toRegionId
                && currentRegionId == attempt.edge.toRegionId) {
            failures.remove(attempt.edge);
            attempt = null;
            return null;
        }
        double distanceToEnd = position.distance(attempt.edge.endPoint());
        if (distanceToEnd + Math.max(0, progressTolerancePx) < attempt.bestDistanceToEndPx) {
            attempt = new Attempt(attempt.edge, attempt.startedRegionId,
                    distanceToEnd, nowMs);
            return null;
        }
        if (nowMs - attempt.lastProgressAtMs < Math.max(1L, timeoutMs)) {
            return null;
        }
        AgentNavigationGraph.Edge failed = attempt.edge.toEdge(attempt.startedRegionId);
        attempt = null;
        return failed;
    }

    public synchronized int trackedEdgeCount(int currentMapId, long nowMs, long retentionMs) {
        synchronizeMap(currentMapId);
        expire(nowMs, retentionMs);
        return failures.size();
    }

    public synchronized RoutingView routingView(int currentMapId,
                                                long nowMs,
                                                long retentionMs,
                                                int perFailure,
                                                int maximumPenalty) {
        synchronizeMap(currentMapId);
        expire(nowMs, retentionMs);
        java.util.Set<EdgeSignature> suppressed = new HashSet<>();
        Map<EdgeSignature, Integer> penalties = new HashMap<>();
        for (Map.Entry<EdgeSignature, Failure> entry : failures.entrySet()) {
            Failure failure = entry.getValue();
            if (failure.suppressedUntilMs > nowMs) {
                suppressed.add(entry.getKey());
            }
            long value = (long) Math.max(0, perFailure) * failure.count;
            penalties.put(entry.getKey(),
                    (int) Math.min(Math.max(0, maximumPenalty), value));
        }
        return new RoutingView(java.util.Set.copyOf(suppressed), Map.copyOf(penalties));
    }

    public synchronized Snapshot snapshot(long nowMs,
                                          long retentionMs,
                                          int perFailure,
                                          int maximumPenalty) {
        expire(nowMs, retentionMs);
        java.util.List<EdgeFailure> edges = new ArrayList<>(failures.size());
        for (Map.Entry<EdgeSignature, Failure> entry : failures.entrySet()) {
            EdgeSignature edge = entry.getKey();
            Failure failure = entry.getValue();
            long rawPenalty = (long) Math.max(0, perFailure) * failure.count;
            edges.add(new EdgeFailure(
                    edge.toRegionId, edge.type,
                    edge.startX, edge.startY, edge.endX, edge.endY,
                    failure.count,
                    (int) Math.min(Math.max(0, maximumPenalty), rawPenalty),
                    failure.suppressedUntilMs));
        }
        return new Snapshot(mapId, java.util.List.copyOf(edges));
    }

    private void expire(long nowMs, long retentionMs) {
        long boundedRetention = Math.max(1L, retentionMs);
        Iterator<Map.Entry<EdgeSignature, Failure>> iterator = failures.entrySet().iterator();
        while (iterator.hasNext()) {
            Failure failure = iterator.next().getValue();
            if (nowMs - failure.lastFailureAtMs >= boundedRetention
                    && failure.suppressedUntilMs <= nowMs) {
                iterator.remove();
            }
        }
    }

    private void trim(int maximum) {
        while (failures.size() > maximum) {
            Iterator<EdgeSignature> iterator = failures.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    static boolean isRisky(AgentNavigationGraph.Edge edge) {
        return edge != null && switch (edge.type) {
            case JUMP, FLASH_JUMP, DROP, CLIMB -> true;
            default -> false;
        };
    }

    private record Failure(int count, long lastFailureAtMs, long suppressedUntilMs) {
    }

    public static final class RoutingView {
        private final java.util.Set<EdgeSignature> suppressed;
        private final Map<EdgeSignature, Integer> penalties;

        private RoutingView(java.util.Set<EdgeSignature> suppressed,
                            Map<EdgeSignature, Integer> penalties) {
            this.suppressed = suppressed;
            this.penalties = penalties;
        }

        public boolean allows(AgentNavigationGraph.Edge edge) {
            return edge == null || !suppressed.contains(EdgeSignature.of(edge));
        }

        public int penalty(AgentNavigationGraph.Edge edge) {
            return edge == null ? 0 : penalties.getOrDefault(EdgeSignature.of(edge), 0);
        }
    }

    public record Snapshot(int mapId, java.util.List<EdgeFailure> edges) {
        public Snapshot {
            edges = edges == null ? java.util.List.of() : java.util.List.copyOf(edges);
        }

        public int trackedEdgeCount() {
            return edges.size();
        }
    }

    public record EdgeFailure(int toRegionId,
                              AgentNavigationGraph.EdgeType type,
                              int startX,
                              int startY,
                              int endX,
                              int endY,
                              int failureCount,
                              int penaltyMs,
                              long suppressedUntilMs) {
    }

    private record Attempt(EdgeSignature edge,
                           int startedRegionId,
                           double bestDistanceToEndPx,
                           long lastProgressAtMs) {
    }

    private record EdgeSignature(int toRegionId,
                                 AgentNavigationGraph.EdgeType type,
                                 int startX,
                                 int startY,
                                 int endX,
                                 int endY,
                                 int launchMinX,
                                 int launchMaxX,
                                 int launchStepX,
                                 int ropeX,
                                 int ropeTopY,
                                 int ropeBottomY) {
        static EdgeSignature of(AgentNavigationGraph.Edge edge) {
            return new EdgeSignature(edge.toRegionId, edge.type,
                    edge.startPoint.x, edge.startPoint.y, edge.endPoint.x, edge.endPoint.y,
                    edge.launchMinX, edge.launchMaxX, edge.launchStepX,
                    edge.ropeX, edge.ropeTopY, edge.ropeBottomY);
        }

        boolean matches(AgentNavigationGraph.Edge edge) {
            return edge != null && equals(of(edge));
        }

        Point endPoint() {
            return new Point(endX, endY);
        }

        AgentNavigationGraph.Edge toEdge(int fromRegionId) {
            return new AgentNavigationGraph.Edge(fromRegionId, toRegionId, type,
                    new Point(startX, startY), new Point(endX, endY),
                    launchMinX, launchMaxX, launchStepX, -1,
                    ropeX, ropeTopY, ropeBottomY, 0);
        }
    }
}
