package server.agents.coordination.session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded interaction-session registry shared by trade, party, social, guild,
 * expedition, and market protocols.
 */
public final class AgentInteractionSessionRegistry {
    private static final int MAX_SESSIONS = config.AgentTuning.intValue(
            "server.agents.coordination.session.AgentInteractionSessionRegistry.MAX_SESSIONS");
    private static final AtomicLong NEXT_ID = new AtomicLong();
    private static final Map<Long, MutableSession> SESSIONS = new LinkedHashMap<>();

    private AgentInteractionSessionRegistry() {
    }

    public static synchronized AgentInteractionSessionSnapshot propose(
            AgentInteractionSessionType type,
            int initiatorCharacterId,
            Set<Integer> participantCharacterIds,
            long nowMs,
            long ttlMs,
            String correlationId,
            Map<String, String> attributes) {
        if (type == null || initiatorCharacterId <= 0 || participantCharacterIds == null
                || participantCharacterIds.isEmpty() || nowMs < 0 || ttlMs <= 0) {
            throw new IllegalArgumentException("Valid interaction proposal is required");
        }
        expire(nowMs);
        evictTerminal();
        if (SESSIONS.size() >= MAX_SESSIONS) {
            throw new IllegalStateException("Agent interaction session capacity reached");
        }
        Set<Integer> participants = new LinkedHashSet<>(participantCharacterIds);
        participants.add(initiatorCharacterId);
        if (participants.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Interaction participants require character ids");
        }
        long id = NEXT_ID.incrementAndGet();
        String correlation = correlationId == null || correlationId.isBlank()
                ? "interaction:" + id : correlationId.trim();
        MutableSession session = new MutableSession(
                id, correlation, type, initiatorCharacterId, participants,
                nowMs, saturatedAdd(nowMs, ttlMs),
                attributes == null ? Map.of() : Map.copyOf(attributes));
        session.accepted.add(initiatorCharacterId);
        if (session.accepted.containsAll(session.participants)) {
            session.status = AgentInteractionSessionStatus.ACTIVE;
        }
        SESSIONS.put(id, session);
        return session.snapshot();
    }

    public static synchronized AgentInteractionSessionSnapshot accept(
            long sessionId, int characterId, long nowMs) {
        MutableSession session = requireLive(sessionId, nowMs);
        requireParticipant(session, characterId);
        session.accepted.add(characterId);
        session.updatedAtMs = nowMs;
        if (session.accepted.containsAll(session.participants)) {
            session.status = AgentInteractionSessionStatus.ACTIVE;
        }
        return session.snapshot();
    }

    public static synchronized AgentInteractionSessionSnapshot decline(
            long sessionId, int characterId, long nowMs, String reason) {
        MutableSession session = requireLive(sessionId, nowMs);
        requireParticipant(session, characterId);
        session.status = AgentInteractionSessionStatus.DECLINED;
        session.updatedAtMs = nowMs;
        session.reason = reason == null ? "" : reason;
        return session.snapshot();
    }

    public static synchronized AgentInteractionSessionSnapshot complete(
            long sessionId, long nowMs, String reason) {
        MutableSession session = requireLive(sessionId, nowMs);
        if (session.status != AgentInteractionSessionStatus.ACTIVE) {
            throw new IllegalStateException("Only an active interaction can complete");
        }
        session.status = AgentInteractionSessionStatus.COMPLETED;
        session.updatedAtMs = nowMs;
        session.reason = reason == null ? "" : reason;
        return session.snapshot();
    }

    public static synchronized AgentInteractionSessionSnapshot cancel(
            long sessionId, long nowMs, String reason) {
        MutableSession session = requireLive(sessionId, nowMs);
        session.status = AgentInteractionSessionStatus.CANCELLED;
        session.updatedAtMs = nowMs;
        session.reason = reason == null ? "" : reason;
        return session.snapshot();
    }

    public static synchronized AgentInteractionSessionSnapshot find(long sessionId, long nowMs) {
        expire(nowMs);
        MutableSession session = SESSIONS.get(sessionId);
        return session == null ? null : session.snapshot();
    }

    public static synchronized List<AgentInteractionSessionSnapshot> forParticipant(
            int characterId, long nowMs) {
        expire(nowMs);
        List<AgentInteractionSessionSnapshot> result = new ArrayList<>();
        for (MutableSession session : SESSIONS.values()) {
            if (session.participants.contains(characterId)) {
                result.add(session.snapshot());
            }
        }
        return List.copyOf(result);
    }

    public static synchronized int size(long nowMs) {
        expire(nowMs);
        return SESSIONS.size();
    }

    static synchronized void resetForTests() {
        SESSIONS.clear();
        NEXT_ID.set(0L);
    }

    private static MutableSession requireLive(long sessionId, long nowMs) {
        expire(nowMs);
        MutableSession session = SESSIONS.get(sessionId);
        if (session == null || session.status.terminal()) {
            throw new IllegalStateException("Interaction session is not live: " + sessionId);
        }
        return session;
    }

    private static void requireParticipant(MutableSession session, int characterId) {
        if (!session.participants.contains(characterId)) {
            throw new IllegalArgumentException("Character is not an interaction participant");
        }
    }

    private static void expire(long nowMs) {
        for (MutableSession session : SESSIONS.values()) {
            if (!session.status.terminal() && nowMs >= session.expiresAtMs) {
                session.status = AgentInteractionSessionStatus.EXPIRED;
                session.updatedAtMs = nowMs;
                session.reason = "session lifetime elapsed";
            }
        }
    }

    private static void evictTerminal() {
        SESSIONS.entrySet().removeIf(entry -> entry.getValue().status.terminal());
    }

    private static long saturatedAdd(long value, long increment) {
        return Long.MAX_VALUE - value < increment ? Long.MAX_VALUE : value + increment;
    }

    private static final class MutableSession {
        private final long id;
        private final String correlationId;
        private final AgentInteractionSessionType type;
        private final int initiator;
        private final Set<Integer> participants;
        private final Set<Integer> accepted = new LinkedHashSet<>();
        private final long createdAtMs;
        private final long expiresAtMs;
        private final Map<String, String> attributes;
        private AgentInteractionSessionStatus status = AgentInteractionSessionStatus.PROPOSED;
        private long updatedAtMs;
        private String reason = "";

        private MutableSession(
                long id,
                String correlationId,
                AgentInteractionSessionType type,
                int initiator,
                Set<Integer> participants,
                long createdAtMs,
                long expiresAtMs,
                Map<String, String> attributes) {
            this.id = id;
            this.correlationId = correlationId;
            this.type = type;
            this.initiator = initiator;
            this.participants = Set.copyOf(participants);
            this.createdAtMs = createdAtMs;
            this.expiresAtMs = expiresAtMs;
            this.updatedAtMs = createdAtMs;
            this.attributes = attributes;
        }

        private AgentInteractionSessionSnapshot snapshot() {
            return new AgentInteractionSessionSnapshot(
                    id, correlationId, type, status, initiator, participants,
                    Set.copyOf(accepted), createdAtMs, expiresAtMs, updatedAtMs,
                    reason, attributes);
        }
    }
}
