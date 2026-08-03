package server.agents.progression;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Session route health; durable objectives recalculate it from the live map after relog. */
final class AgentVictoriaRouteState {
    private static final long EDGE_FAILURE_MEMORY_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentVictoriaRouteState.EDGE_FAILURE_MEMORY_MS");
    static final AgentCapabilityStateKey<AgentVictoriaRouteState> STATE_KEY =
            new AgentCapabilityStateKey<>("progression.victoria-route", AgentVictoriaRouteState.class,
                    AgentVictoriaRouteState::new);

    private final Map<Long, Long> blockedEdgesUntilMs = new HashMap<>();
    private long failingEdge;
    private int consecutiveFailures;
    private long lastFailureAtMs;
    private int lastObservedMapId;
    private long lastMapProgressAtMs;
    private int activeTravelMapId = -1;
    private int activeDestinationMapId = -1;
    private int arrivalSettleMapId = -1;
    private long arrivalSettleUntilMs;
    private long arrivalObserverGraceUntilMs;
    private long arrivalVisibleSettleDurationMs;
    private boolean arrivalAwaitingObserver;

    synchronized Set<Long> blockedEdges(long nowMs) {
        blockedEdgesUntilMs.entrySet().removeIf(entry -> entry.getValue() <= nowMs);
        return Set.copyOf(blockedEdgesUntilMs.keySet());
    }

    synchronized void observeMap(int mapId, long nowMs) {
        if (lastObservedMapId != mapId) {
            lastObservedMapId = mapId;
            lastMapProgressAtMs = nowMs;
            failingEdge = 0L;
            consecutiveFailures = 0;
            lastFailureAtMs = 0L;
        } else if (lastMapProgressAtMs == 0L) {
            lastMapProgressAtMs = nowMs;
        }
    }

    synchronized boolean recordFailure(long edge, long nowMs, long blockDurationMs) {
        if (failingEdge == edge && nowMs - lastFailureAtMs < EDGE_FAILURE_MEMORY_MS) {
            return false;
        }
        if (failingEdge != edge) {
            failingEdge = edge;
            consecutiveFailures = 1;
        } else {
            consecutiveFailures++;
        }
        lastFailureAtMs = nowMs;
        if (consecutiveFailures < 3) {
            return false;
        }
        blockedEdgesUntilMs.put(edge, nowMs + blockDurationMs);
        failingEdge = 0L;
        consecutiveFailures = 0;
        lastFailureAtMs = 0L;
        return true;
    }

    synchronized void recordPortalSuccess(int destinationMapId, long nowMs, long settleDurationMs) {
        recordPortalSuccess(destinationMapId, nowMs, settleDurationMs, false, 0L);
    }

    synchronized void recordPortalSuccess(int destinationMapId,
                                          long nowMs,
                                          long settleDurationMs,
                                          boolean awaitObserver,
                                          long observerGraceMs) {
        lastMapProgressAtMs = nowMs;
        clearActiveTravel();
        arrivalSettleMapId = destinationMapId;
        arrivalVisibleSettleDurationMs = Math.max(0L, settleDurationMs);
        arrivalSettleUntilMs = nowMs + arrivalVisibleSettleDurationMs;
        arrivalAwaitingObserver = awaitObserver;
        arrivalObserverGraceUntilMs = awaitObserver
                ? nowMs + Math.max(arrivalVisibleSettleDurationMs, observerGraceMs)
                : 0L;
        failingEdge = 0L;
        consecutiveFailures = 0;
        lastFailureAtMs = 0L;
    }

    synchronized boolean settlingAt(int mapId, long nowMs) {
        return settlingAt(mapId, nowMs, false);
    }

    synchronized boolean settlingAt(int mapId, long nowMs, boolean observedByPlayer) {
        if (arrivalSettleMapId < 0) {
            return false;
        }
        if (arrivalSettleMapId != mapId) {
            clearArrivalSettle();
            return false;
        }
        if (nowMs < arrivalSettleUntilMs) {
            return true;
        }
        if (arrivalAwaitingObserver) {
            if (observedByPlayer) {
                arrivalAwaitingObserver = false;
                arrivalObserverGraceUntilMs = 0L;
                arrivalSettleUntilMs = nowMs + arrivalVisibleSettleDurationMs;
                return arrivalVisibleSettleDurationMs > 0L;
            }
            if (nowMs < arrivalObserverGraceUntilMs) {
                return true;
            }
        }
        clearArrivalSettle();
        return false;
    }

    private void clearArrivalSettle() {
        arrivalSettleMapId = -1;
        arrivalSettleUntilMs = 0L;
        arrivalObserverGraceUntilMs = 0L;
        arrivalVisibleSettleDurationMs = 0L;
        arrivalAwaitingObserver = false;
    }

    synchronized long lastMapProgressAtMs() {
        return lastMapProgressAtMs;
    }

    synchronized void markActiveTravel(int sourceMapId, int destinationMapId) {
        activeTravelMapId = sourceMapId;
        activeDestinationMapId = destinationMapId;
    }

    synchronized boolean activeTravelIn(int mapId) {
        return activeTravelMapId == mapId && activeDestinationMapId >= 0
                && activeDestinationMapId != mapId;
    }

    synchronized void clearActiveTravel() {
        activeTravelMapId = -1;
        activeDestinationMapId = -1;
    }
}
