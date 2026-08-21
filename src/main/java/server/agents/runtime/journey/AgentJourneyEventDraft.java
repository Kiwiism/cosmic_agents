package server.agents.runtime.journey;

import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.Map;

/** Unsequenced event submitted to the durable per-Agent journal. */
public record AgentJourneyEventDraft(
        String eventId,
        String agentId,
        int characterId,
        long occurredAtMs,
        AgentJourneyEventType type,
        AgentActivityKind activityKind,
        String source,
        String correlationId,
        String reason,
        Map<String, String> evidence) {

    public AgentJourneyEventDraft {
        eventId = text(eventId);
        agentId = text(agentId);
        source = text(source);
        correlationId = text(correlationId);
        reason = text(reason);
        evidence = Map.copyOf(evidence == null ? Map.of() : evidence);
        if (eventId.isEmpty() || agentId.isEmpty() || characterId <= 0
                || occurredAtMs < 0L || type == null || source.isEmpty()
                || correlationId.isEmpty()
                || evidence.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null)) {
            throw new IllegalArgumentException("complete journey event evidence is required");
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
