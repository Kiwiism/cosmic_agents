package server.agents.runtime.activity.session;

import server.agents.runtime.activity.outcome.AgentActivityOutcomeEnvelope;
import server.agents.runtime.journey.AgentJourneyEvent;
import server.agents.runtime.journey.AgentJourneyEventDraft;
import server.agents.runtime.journey.AgentJourneyEventType;
import server.agents.runtime.journey.AgentJourneyJournalStore;

import java.util.LinkedHashMap;
import java.util.Map;

/** Records a child system's terminal result in the shared Agent journey. */
public final class AgentActivityTerminalJourneyRecorder {
    private final AgentJourneyJournalStore journal;

    public AgentActivityTerminalJourneyRecorder(AgentJourneyJournalStore journal) {
        if (journal == null) throw new IllegalArgumentException("journey journal is required");
        this.journal = journal;
    }

    public AgentJourneyEvent record(AgentActivityOutcomeEnvelope envelope) {
        if (envelope == null) throw new IllegalArgumentException("activity outcome is required");
        AgentActivityTerminalOutcome outcome = envelope.outcome();
        int characterId;
        try {
            characterId = Integer.parseInt(outcome.agentId());
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Cosmic activity outcome requires numeric Agent id");
        }
        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("phase", outcome.phase().name());
        evidence.put("retryable", Boolean.toString(outcome.retryable()));
        evidence.put("startedAtMs", Long.toString(outcome.startedAtMs()));
        evidence.put("endedAtMs", Long.toString(outcome.endedAtMs()));
        outcome.evidence().forEach((key, value) ->
                evidence.put(key, value == null ? "" : String.valueOf(value)));
        return journal.append(new AgentJourneyEventDraft(
                "activity-terminal:" + envelope.outcomeId(), outcome.agentId(), characterId,
                outcome.endedAtMs(), AgentJourneyEventType.ACTIVITY_TERMINAL,
                outcome.kind(), "activity-host", outcome.sessionId(), outcome.reason(), evidence));
    }
}
