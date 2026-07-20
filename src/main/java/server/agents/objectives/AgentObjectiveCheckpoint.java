package server.agents.objectives;

import java.util.List;

/** Durable intent above transient capability frames. */
public record AgentObjectiveCheckpoint(
        int schemaVersion,
        int characterId,
        long updatedAtMs,
        AgentObjectiveDefinition activeObjective,
        List<AgentObjectiveSuspension> suspendedObjectives,
        List<AgentObjectiveJournalEntry> journal) {

    public AgentObjectiveCheckpoint {
        if (schemaVersion <= 0 || characterId <= 0 || updatedAtMs < 0) {
            throw new IllegalArgumentException("valid objective checkpoint identity is required");
        }
        suspendedObjectives = suspendedObjectives == null ? List.of() : List.copyOf(suspendedObjectives);
        journal = journal == null ? List.of() : List.copyOf(journal);
    }
}
