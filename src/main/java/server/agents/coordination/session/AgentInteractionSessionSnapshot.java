package server.agents.coordination.session;

import java.util.Map;
import java.util.Set;

/** Immutable interaction protocol state suitable for policy and LLM context. */
public record AgentInteractionSessionSnapshot(
        long sessionId,
        String correlationId,
        AgentInteractionSessionType type,
        AgentInteractionSessionStatus status,
        int initiatorCharacterId,
        Set<Integer> participantCharacterIds,
        Set<Integer> acceptedCharacterIds,
        long createdAtMs,
        long expiresAtMs,
        long updatedAtMs,
        String reason,
        Map<String, String> attributes) {

    public AgentInteractionSessionSnapshot {
        participantCharacterIds = Set.copyOf(participantCharacterIds);
        acceptedCharacterIds = Set.copyOf(acceptedCharacterIds);
        attributes = Map.copyOf(attributes);
        reason = reason == null ? "" : reason;
    }
}
