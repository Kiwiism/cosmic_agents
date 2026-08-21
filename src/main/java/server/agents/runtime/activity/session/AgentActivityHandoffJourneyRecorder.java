package server.agents.runtime.activity.session;

import server.agents.runtime.journey.AgentJourneyEvent;
import server.agents.runtime.journey.AgentJourneyEventDraft;
import server.agents.runtime.journey.AgentJourneyEventType;
import server.agents.runtime.journey.AgentJourneyJournalStore;

import java.util.Map;

/** Records each durable handoff phase, including failures that need safe fallback. */
public final class AgentActivityHandoffJourneyRecorder {
    private final AgentJourneyJournalStore journal;

    public AgentActivityHandoffJourneyRecorder(AgentJourneyJournalStore journal) {
        if (journal == null) throw new IllegalArgumentException("journey journal is required");
        this.journal = journal;
    }

    public AgentJourneyEvent record(
            int characterId, AgentActivityHandoffCoordinator.Handoff handoff) {
        if (handoff == null) throw new IllegalArgumentException("activity handoff is required");
        return journal.append(new AgentJourneyEventDraft(
                "handoff:" + handoff.handoffId() + ':' + handoff.phase() + ':' + handoff.updatedAtMs(),
                handoff.agentId(), characterId, handoff.updatedAtMs(),
                AgentJourneyEventType.HANDOFF_PHASE_CHANGED, handoff.targetKind(),
                "activity-handoff", handoff.handoffId(), handoff.reason(),
                Map.of("phase", handoff.phase().name(),
                        "sourceKind", handoff.sourceKind().name(),
                        "targetKind", handoff.targetKind().name(),
                        "sourceReleased", Boolean.toString(handoff.sourceReleased()),
                        "requiresSafeFallback", Boolean.toString(handoff.requiresSafeFallback()),
                        "deadlineMs", Long.toString(handoff.deadlineMs()))));
    }
}
