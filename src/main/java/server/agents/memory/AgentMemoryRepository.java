package server.agents.memory;

import java.util.List;

public interface AgentMemoryRepository {
    void remember(AgentMemoryEntry memory, long nowMs);

    AgentMemoryEntry recall(AgentMemoryKind kind, String key, long nowMs);

    List<AgentMemoryEntry> recallAll(AgentMemoryKind kind, long nowMs);

    boolean forget(AgentMemoryKind kind, String key);
}
