package server.agents.diagnostics;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.ConcurrentHashMap;

/** Optional event sink for run-level diagnostics; inactive Agents pay only map/recovery boundary lookups. */
public final class AgentRunObservationRuntime {
    public interface Listener {
        default void onMapChanged(Character agent, int mapId, long nowMs) {
        }

        default void onRecovery(Character agent, String recoveryType, long nowMs) {
        }
    }

    private static final ConcurrentHashMap<Integer, Listener> LISTENERS = new ConcurrentHashMap<>();

    private AgentRunObservationRuntime() {
    }

    public static void register(int agentId, Listener listener) {
        if (agentId > 0 && listener != null) {
            LISTENERS.put(agentId, listener);
        }
    }

    public static void unregister(int agentId, Listener listener) {
        if (agentId > 0 && listener != null) {
            LISTENERS.remove(agentId, listener);
        }
    }

    public static void unregister(int agentId) {
        if (agentId > 0) {
            LISTENERS.remove(agentId);
        }
    }

    public static void mapChanged(AgentRuntimeEntry entry, Character agent, long nowMs) {
        publish(entry, agent, listener -> listener.onMapChanged(agent, agent.getMapId(), nowMs));
    }

    public static void recovery(AgentRuntimeEntry entry, Character agent, String recoveryType, long nowMs) {
        publish(entry, agent, listener -> listener.onRecovery(agent, recoveryType, nowMs));
    }

    private static void publish(AgentRuntimeEntry entry,
                                Character agent,
                                java.util.function.Consumer<Listener> event) {
        if (entry == null || agent == null) {
            return;
        }
        Listener listener = LISTENERS.get(agent.getId());
        if (listener == null) {
            return;
        }
        try {
            event.accept(listener);
        } catch (RuntimeException ignored) {
            // Diagnostics must never alter Agent execution.
        }
    }
}
