package server.agents.capabilities.navigation;

import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Applies configuration to one Agent's reliability state. */
public final class AgentNavigationEdgeReliabilityRuntime {
    private AgentNavigationEdgeReliabilityRuntime() {
    }

    public static Predicate<AgentNavigationGraph.Edge> edgeFilter(
            AgentRuntimeEntry entry, int mapId, long nowMs) {
        if (!AgentNavigationReliabilityConfig.edgeSuppressionEnabled() || entry == null) {
            return edge -> true;
        }
        AgentNavigationEdgeReliabilityState.RoutingView view = state(entry).routingView(
                mapId, nowMs, AgentNavigationReliabilityConfig.failureRetentionMs(),
                AgentNavigationReliabilityConfig.failurePenaltyMs(),
                AgentNavigationReliabilityConfig.maxEdgePenaltyMs());
        return view::allows;
    }

    public static ToIntFunction<AgentNavigationGraph.Edge> edgePenalty(
            AgentRuntimeEntry entry, int mapId, long nowMs) {
        if (!AgentNavigationReliabilityConfig.routePenaltiesEnabled() || entry == null) {
            return edge -> 0;
        }
        AgentNavigationEdgeReliabilityState.RoutingView view = state(entry).routingView(
                mapId, nowMs, AgentNavigationReliabilityConfig.failureRetentionMs(),
                AgentNavigationReliabilityConfig.failurePenaltyMs(),
                AgentNavigationReliabilityConfig.maxEdgePenaltyMs());
        return view::penalty;
    }

    public static boolean suppressed(AgentRuntimeEntry entry, int mapId,
                                     AgentNavigationGraph.Edge edge, long nowMs) {
        return AgentNavigationReliabilityConfig.edgeSuppressionEnabled() && entry != null
                && state(entry).isSuppressed(mapId, edge, nowMs,
                AgentNavigationReliabilityConfig.failureRetentionMs());
    }

    public static void failed(AgentRuntimeEntry entry, int mapId,
                              AgentNavigationGraph.Edge edge, long nowMs) {
        if (entry == null || !AgentNavigationReliabilityConfig.tracksReliability()) {
            return;
        }
        state(entry).recordFailure(mapId, edge, nowMs,
                AgentNavigationReliabilityConfig.failureThreshold(),
                AgentNavigationReliabilityConfig.suppressionMs(),
                AgentNavigationReliabilityConfig.failureRetentionMs(),
                AgentNavigationReliabilityConfig.maxTrackedEdges());
    }

    public static void beganAttempt(AgentRuntimeEntry entry, int mapId,
                                    AgentNavigationGraph.Edge edge, int regionId,
                                    Point position, long nowMs) {
        if (entry != null && AgentNavigationReliabilityConfig.tracksReliability()) {
            state(entry).beginAttempt(mapId, edge, regionId, position, nowMs);
        }
    }

    public static AgentNavigationGraph.Edge observeAttempt(AgentRuntimeEntry entry,
                                                            int mapId,
                                                            int regionId,
                                                            Point position,
                                                            long nowMs) {
        if (entry == null || !AgentNavigationReliabilityConfig.tracksReliability()) {
            return null;
        }
        AgentNavigationGraph.Edge timedOut = state(entry).observeAttempt(
                mapId, regionId, position, nowMs,
                AgentNavigationReliabilityConfig.attemptTimeoutMs(),
                AgentNavigationReliabilityConfig.progressTolerancePx());
        if (timedOut != null) {
            failed(entry, mapId, timedOut, nowMs);
        }
        return timedOut;
    }

    private static AgentNavigationEdgeReliabilityState state(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentNavigationEdgeReliabilityState.STATE_KEY);
    }
}
