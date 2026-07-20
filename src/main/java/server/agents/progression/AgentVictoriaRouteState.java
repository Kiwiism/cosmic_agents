package server.agents.progression;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Session route health; durable objectives recalculate it from the live map after relog. */
final class AgentVictoriaRouteState {
    static final AgentCapabilityStateKey<AgentVictoriaRouteState> STATE_KEY =
            new AgentCapabilityStateKey<>("progression.victoria-route", AgentVictoriaRouteState.class,
                    AgentVictoriaRouteState::new);

    private final Map<Long, Long> blockedEdgesUntilMs = new HashMap<>();
    private long failingEdge;
    private int consecutiveFailures;
    private long lastFailureAtMs;
    private int lastObservedMapId;
    private long lastMapProgressAtMs;

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
        if (failingEdge == edge && nowMs - lastFailureAtMs < 2_000L) {
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

    synchronized void recordPortalSuccess(long nowMs) {
        lastMapProgressAtMs = nowMs;
        failingEdge = 0L;
        consecutiveFailures = 0;
        lastFailureAtMs = 0L;
    }

    synchronized long lastMapProgressAtMs() {
        return lastMapProgressAtMs;
    }
}
