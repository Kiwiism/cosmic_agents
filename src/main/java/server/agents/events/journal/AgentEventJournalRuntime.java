package server.agents.events.journal;

import server.agents.events.AgentEvent;

import java.util.List;

/** Lazily owns the optional process-wide durable event writer. */
public final class AgentEventJournalRuntime {
    private static AgentEventJournalConfig config;
    private static BoundedAgentEventJournal journal;

    private AgentEventJournalRuntime() {
    }

    public static synchronized boolean offer(AgentEvent event) {
        AgentEventJournalConfig resolved = config();
        if (!resolved.enabled()) {
            return false;
        }
        if (journal == null) {
            journal = new BoundedAgentEventJournal(resolved);
        }
        return journal.offer(event);
    }

    public static synchronized AgentEventJournalSnapshot snapshot() {
        AgentEventJournalConfig resolved = config();
        if (journal == null) {
            return new AgentEventJournalSnapshot(resolved.enabled(), resolved.capacity(),
                    0, 0L, 0L, 0L, 0L);
        }
        return journal.snapshot();
    }

    /** Explicit blocking diagnostic/offline read; never call from an Agent tick or event listener. */
    public static synchronized List<AgentEventJournalRecord> replay(AgentEventReplayQuery query) {
        return new AgentEventJournalReplayReader(config().path()).query(query);
    }

    static synchronized void resetForTests() {
        if (journal != null) {
            journal.close();
        }
        journal = null;
        config = null;
    }

    private static AgentEventJournalConfig config() {
        if (config == null) {
            config = AgentEventJournalConfig.fromSystemProperties();
        }
        return config;
    }
}
