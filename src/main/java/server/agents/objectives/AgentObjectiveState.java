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
    final ArrayDeque<AgentObjectiveSuspension> suspended = new ArrayDeque<>();
    final ArrayDeque<AgentObjectiveJournalEntry> journal = new ArrayDeque<>();

    public synchronized AgentObjectiveDefinition active() {
        return active;
    }

    public synchronized List<AgentObjectiveJournalEntry> journalSnapshot() {
        return List.copyOf(journal);
    }

    public synchronized List<AgentObjectiveSuspension> suspendedSnapshot() {
        return List.copyOf(suspended);
    }

    public synchronized AgentObjectiveCheckpoint checkpoint(int characterId, long nowMs) {
        return new AgentObjectiveCheckpoint(1, characterId, nowMs, active,
                List.copyOf(suspended), List.copyOf(journal));
    }

    public synchronized void restore(AgentObjectiveCheckpoint checkpoint) {
        active = checkpoint.activeObjective();
        suspended.clear();
        suspended.addAll(checkpoint.suspendedObjectives());
        journal.clear();
        List<AgentObjectiveJournalEntry> restoredJournal = checkpoint.journal();
        int start = Math.max(0, restoredJournal.size() - MAX_JOURNAL);
        journal.addAll(restoredJournal.subList(start, restoredJournal.size()));
    }
}
