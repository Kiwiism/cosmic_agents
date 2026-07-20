package server.agents.capabilities.reactor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Prevents a cohort of Agents from all walking to the same live reactor. */
public final class AgentReactorTargetReservationRuntime {
    private static final long RESERVATION_TTL_MS = 30_000L;
    private static final Map<Integer, Reservation> BY_AGENT = new HashMap<>();
    private static final Map<TargetKey, Integer> OWNER_BY_TARGET = new HashMap<>();

    private AgentReactorTargetReservationRuntime() {
    }

    public static synchronized Optional<AgentReactorTarget> reserveNearest(
            int agentId,
            Object mapScope,
            List<AgentReactorTarget> nearestFirst) {
        long nowMs = System.currentTimeMillis();
        removeExpired(nowMs);
        List<AgentReactorTarget> candidates = nearestFirst == null ? List.of() : nearestFirst;
        Reservation existing = BY_AGENT.get(agentId);
        if (existing != null) {
            Optional<AgentReactorTarget> retained = candidates.stream()
                    .filter(candidate -> existing.key().equals(key(mapScope, candidate.objectId())))
                    .findFirst();
            if (retained.isPresent()) {
                BY_AGENT.put(agentId, new Reservation(existing.key(), nowMs + RESERVATION_TTL_MS));
                return retained;
            }
            release(agentId);
        }

        for (AgentReactorTarget candidate : candidates) {
            TargetKey key = key(mapScope, candidate.objectId());
            Integer owner = OWNER_BY_TARGET.get(key);
            if (owner == null || owner == agentId) {
                OWNER_BY_TARGET.put(key, agentId);
                BY_AGENT.put(agentId, new Reservation(key, nowMs + RESERVATION_TTL_MS));
                return Optional.of(candidate);
            }
        }

        // A live reactor can only satisfy one hit sequence at a time. Returning no target lets
        // the objective wait/replan instead of sending a cohort to the same respawning box.
        return Optional.empty();
    }

    public static synchronized void release(int agentId) {
        Reservation reservation = BY_AGENT.remove(agentId);
        if (reservation != null) {
            OWNER_BY_TARGET.remove(reservation.key(), agentId);
        }
    }

    static synchronized void clear() {
        BY_AGENT.clear();
        OWNER_BY_TARGET.clear();
    }

    private static void removeExpired(long nowMs) {
        BY_AGENT.entrySet().removeIf(entry -> {
            if (entry.getValue().expiresAtMs() > nowMs) {
                return false;
            }
            OWNER_BY_TARGET.remove(entry.getValue().key(), entry.getKey());
            return true;
        });
    }

    private static TargetKey key(Object mapScope, int objectId) {
        return new TargetKey(mapScope, objectId);
    }

    private record TargetKey(Object mapScope, int objectId) {
    }

    private record Reservation(TargetKey key, long expiresAtMs) {
    }
}
