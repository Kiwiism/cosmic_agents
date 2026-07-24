package server.agents.memory;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AgentMemoryState {
    static final AgentCapabilityStateKey<AgentMemoryState> STATE_KEY =
            new AgentCapabilityStateKey<>("runtime.bounded-memory",
                    AgentMemoryState.class, AgentMemoryState::new);
    private static final int MAX_PER_KIND = config.AgentTuning.intValue(
            "server.agents.memory.AgentMemoryState.MAX_PER_KIND");

    private final Map<AgentMemoryKind, LinkedHashMap<String, AgentMemoryEntry>> memories =
            new EnumMap<>(AgentMemoryKind.class);

    synchronized void put(AgentMemoryEntry entry, long nowMs) {
        LinkedHashMap<String, AgentMemoryEntry> bucket =
                memories.computeIfAbsent(entry.kind(), ignored -> new LinkedHashMap<>());
        discardExpired(bucket, nowMs);
        bucket.remove(entry.key());
        while (bucket.size() >= MAX_PER_KIND) {
            String oldest = bucket.keySet().iterator().next();
            bucket.remove(oldest);
        }
        bucket.put(entry.key(), entry);
    }

    synchronized AgentMemoryEntry find(AgentMemoryKind kind, String key, long nowMs) {
        LinkedHashMap<String, AgentMemoryEntry> bucket = memories.get(kind);
        if (bucket == null) {
            return null;
        }
        discardExpired(bucket, nowMs);
        return bucket.get(key);
    }

    synchronized List<AgentMemoryEntry> snapshot(AgentMemoryKind kind, long nowMs) {
        LinkedHashMap<String, AgentMemoryEntry> bucket = memories.get(kind);
        if (bucket == null) {
            return List.of();
        }
        discardExpired(bucket, nowMs);
        return List.copyOf(new ArrayList<>(bucket.values()));
    }

    synchronized boolean remove(AgentMemoryKind kind, String key) {
        LinkedHashMap<String, AgentMemoryEntry> bucket = memories.get(kind);
        return bucket != null && bucket.remove(key) != null;
    }

    private static void discardExpired(
            LinkedHashMap<String, AgentMemoryEntry> bucket, long nowMs) {
        bucket.entrySet().removeIf(entry -> entry.getValue().expired(nowMs));
    }
}
