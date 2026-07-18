package server.agents.runtime.state;

import java.util.Objects;
import java.util.function.Supplier;

/** Typed identity and factory for state owned by one Agent capability. */
public record AgentCapabilityStateKey<T>(String id, Class<T> type, Supplier<? extends T> factory) {
    public AgentCapabilityStateKey {
        if (id == null || id.isBlank() || type == null || factory == null) {
            throw new IllegalArgumentException("Capability state id, type, and factory are required");
        }
        id = id.trim();
    }

    public T create() {
        return Objects.requireNonNull(factory.get(), "Capability state factory returned null for " + id);
    }
}
