package server.agents.runtime.activity.world;

import java.util.List;

public interface AgentWorldDirectorJournalStore {
    void append(AgentWorldDirectorJournalEntry entry);

    List<AgentWorldDirectorJournalEntry> recent(int agentId, int limit);
}
