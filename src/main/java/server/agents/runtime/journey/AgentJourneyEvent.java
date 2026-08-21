package server.agents.runtime.journey;

import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.Map;

/** Durable, replayable event in one Agent's ordered journey. */
public record AgentJourneyEvent(
        int schemaVersion,
        long sequence,
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

    public AgentJourneyEvent {
        AgentJourneyEventDraft draft = new AgentJourneyEventDraft(
                eventId, agentId, characterId, occurredAtMs, type, activityKind,
                source, correlationId, reason, evidence);
        eventId = draft.eventId();
        agentId = draft.agentId();
        source = draft.source();
        correlationId = draft.correlationId();
        reason = draft.reason();
        evidence = draft.evidence();
        if (schemaVersion != 1 || sequence <= 0L) {
            throw new IllegalArgumentException("supported journey schema and positive sequence are required");
        }
    }

    public AgentJourneyEventDraft draft() {
        return new AgentJourneyEventDraft(
                eventId, agentId, characterId, occurredAtMs, type, activityKind,
                source, correlationId, reason, evidence);
    }

    public static AgentJourneyEvent sequence(long sequence, AgentJourneyEventDraft draft) {
        if (draft == null) throw new IllegalArgumentException("journey event draft is required");
        return new AgentJourneyEvent(
                1, sequence, draft.eventId(), draft.agentId(), draft.characterId(),
                draft.occurredAtMs(), draft.type(), draft.activityKind(), draft.source(),
                draft.correlationId(), draft.reason(), draft.evidence());
    }
}
