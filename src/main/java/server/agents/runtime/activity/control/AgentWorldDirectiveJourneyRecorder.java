package server.agents.runtime.activity.control;

import server.agents.runtime.activity.world.AgentWorldDirectiveEnvelope;
import server.agents.runtime.journey.AgentJourneyEvent;
import server.agents.runtime.journey.AgentJourneyEventDraft;
import server.agents.runtime.journey.AgentJourneyEventType;
import server.agents.runtime.journey.AgentJourneyJournalStore;

import java.util.Map;

/** Durable Director command evidence written into the shared per-Agent journey. */
public final class AgentWorldDirectiveJourneyRecorder {
    private final AgentJourneyJournalStore journal;

    public AgentWorldDirectiveJourneyRecorder(AgentJourneyJournalStore journal) {
        if (journal == null) throw new IllegalArgumentException("journey journal is required");
        this.journal = journal;
    }

    public AgentJourneyEvent submitted(AgentWorldDirectiveEnvelope envelope, long nowMs) {
        return record(envelope, AgentJourneyEventType.DIRECTIVE_SUBMITTED,
                "submitted", nowMs);
    }

    public AgentJourneyEvent resolved(AgentWorldDirectiveEnvelope envelope, long nowMs) {
        if (!envelope.status().terminal()) {
            throw new IllegalArgumentException("only a terminal directive can be resolved");
        }
        return record(envelope, AgentJourneyEventType.DIRECTIVE_RESOLVED,
                envelope.status().name().toLowerCase(), nowMs);
    }

    private AgentJourneyEvent record(
            AgentWorldDirectiveEnvelope envelope,
            AgentJourneyEventType type,
            String phase,
            long nowMs) {
        var directive = envelope.directive();
        String eventId = "director:" + directive.directiveId() + ':' + phase;
        return journal.append(new AgentJourneyEventDraft(
                eventId, Integer.toString(directive.agentId()), directive.agentId(), nowMs,
                type, directive.targetActivityKind(), "world-director",
                directive.directiveId(),
                envelope.resolution().isEmpty() ? directive.reason() : envelope.resolution(),
                Map.of("directiveType", directive.type().name(),
                        "status", envelope.status().name(),
                        "requestType", directive.requestType() == null
                                ? "" : directive.requestType().name(),
                        "requestId", directive.requestId(),
                        "revision", Long.toString(envelope.revision()))));
    }
}
