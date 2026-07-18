package server.agents.objectives;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.ArrayDeque;
import java.util.List;

public final class AgentObjectiveState {
    public static final AgentCapabilityStateKey<AgentObjectiveState> STATE_KEY =
            new AgentCapabilityStateKey<>("runtime.objectives", AgentObjectiveState.class,
                    AgentObjectiveState::new);
    static final int MAX_JOURNAL = 256;

    AgentObjectiveDefinition active;
    final ArrayDeque<AgentObjectiveJournalEntry> journal = new ArrayDeque<>();

    public synchronized AgentObjectiveDefinition active() {
        return active;
    }

    public synchronized List<AgentObjectiveJournalEntry> journalSnapshot() {
        return List.copyOf(journal);
    }
}
