package server.agents.runtime.activity.control;

import server.agents.runtime.activity.outcome.AgentActivityOutcomeEnvelope;
import server.agents.runtime.activity.world.AgentWorldActivityAdapterCatalog;
import server.agents.runtime.activity.world.AgentWorldDirectorJournalEntry;

import java.util.List;

/** Read-only model for a future UI; it contains no client or presentation dependency. */
public record AgentWorldDirectorPanelView(
        AgentWorldControlStatus control,
        List<AgentActivityOutcomeEnvelope> pendingOutcomes,
        List<AgentWorldDirectorJournalEntry> recentJournal,
        List<AgentWorldActivityAdapterCatalog.Coverage> activityCoverage) {
    public AgentWorldDirectorPanelView {
        if (control == null) throw new IllegalArgumentException("Director control status is required");
        pendingOutcomes = List.copyOf(pendingOutcomes == null ? List.of() : pendingOutcomes);
        recentJournal = List.copyOf(recentJournal == null ? List.of() : recentJournal);
        activityCoverage = List.copyOf(activityCoverage == null ? List.of() : activityCoverage);
    }
}
