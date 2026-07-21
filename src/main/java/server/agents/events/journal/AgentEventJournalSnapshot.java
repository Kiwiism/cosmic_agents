package server.agents.events.journal;

public record AgentEventJournalSnapshot(
        boolean enabled,
        int capacity,
        int queued,
        long accepted,
        long rejected,
        long written,
        long failures) {
}
