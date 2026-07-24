package server.agents.memory;

/** Bounded, typed memory fact exposed as immutable data. */
public record AgentMemoryEntry(
        AgentMemoryKind kind,
        String key,
        String value,
        double confidence,
        long createdAtMs,
        long expiresAtMs,
        String source) {

    public AgentMemoryEntry {
        if (kind == null || key == null || key.isBlank() || value == null
                || confidence < 0.0 || confidence > 1.0 || createdAtMs < 0
                || expiresAtMs <= createdAtMs || source == null || source.isBlank()) {
            throw new IllegalArgumentException("Valid bounded Agent memory is required");
        }
    }

    public boolean expired(long nowMs) {
        return nowMs >= expiresAtMs;
    }
}
