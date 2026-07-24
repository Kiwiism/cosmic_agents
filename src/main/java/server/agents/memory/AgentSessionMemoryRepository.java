package server.agents.memory;

import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

/** Session-local repository; persistence adapters can implement the same contract later. */
public final class AgentSessionMemoryRepository implements AgentMemoryRepository {
    private final AgentMemoryState state;

    public AgentSessionMemoryRepository(AgentRuntimeEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Agent runtime entry is required");
        }
        this.state = entry.capabilityStates().require(AgentMemoryState.STATE_KEY);
    }

    @Override
    public void remember(AgentMemoryEntry memory, long nowMs) {
        state.put(memory, nowMs);
    }

    @Override
    public AgentMemoryEntry recall(AgentMemoryKind kind, String key, long nowMs) {
        return state.find(kind, key, nowMs);
    }

    @Override
    public List<AgentMemoryEntry> recallAll(AgentMemoryKind kind, long nowMs) {
        return state.snapshot(kind, nowMs);
    }

    @Override
    public boolean forget(AgentMemoryKind kind, String key) {
        return state.remove(kind, key);
    }
}
