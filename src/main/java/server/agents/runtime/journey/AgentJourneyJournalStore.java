package server.agents.runtime.journey;

import java.util.List;

/** Durable append-only port for per-Agent journey reconstruction. */
public interface AgentJourneyJournalStore {
    AgentJourneyEvent append(AgentJourneyEventDraft draft);

    List<AgentJourneyEvent> read(String agentId);
}
