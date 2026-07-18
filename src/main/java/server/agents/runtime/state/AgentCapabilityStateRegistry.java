package server.agents.runtime.state;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-local, typed state registry. Capabilities own their keys; the Agent
 * session owns lifetime and cleanup without knowing each state implementation.
 */
public final class AgentCapabilityStateRegistry {
    private final Map<String, Object> states = new ConcurrentHashMap<>();

    public <T> T require(AgentCapabilityStateKey<T> key) {
        if (key == null) {
            throw new IllegalArgumentException("Capability state key is required");
        }
        Object state = states.computeIfAbsent(key.id(), ignored -> key.create());
        return cast(key, state);
    }

    public <T> Optional<T> find(AgentCapabilityStateKey<T> key) {
        if (key == null) {
            return Optional.empty();
        }
        Object state = states.get(key.id());
        return state == null ? Optional.empty() : Optional.of(cast(key, state));
    }

    public <T> Optional<T> remove(AgentCapabilityStateKey<T> key) {
        if (key == null) {
            return Optional.empty();
        }
        Object state = states.remove(key.id());
        return state == null ? Optional.empty() : Optional.of(cast(key, state));
    }

    public Set<String> registeredStateIds() {
        return Set.copyOf(states.keySet());
    }

    public int size() {
        return states.size();
    }

    public void clear() {
        states.clear();
    }

    private static <T> T cast(AgentCapabilityStateKey<T> key, Object state) {
        if (!key.type().isInstance(state)) {
            throw new IllegalStateException("Capability state key '" + key.id()
                    + "' is already bound to " + state.getClass().getName()
                    + ", not " + key.type().getName());
        }
        return key.type().cast(state);
    }
}
