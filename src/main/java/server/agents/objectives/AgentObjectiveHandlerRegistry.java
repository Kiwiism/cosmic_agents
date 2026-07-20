package server.agents.objectives;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Exact-type registry that keeps durable objective dispatch independent of content runners. */
public final class AgentObjectiveHandlerRegistry {
    private final Map<String, AgentObjectiveHandler> handlers;

    public AgentObjectiveHandlerRegistry(Map<String, AgentObjectiveHandler> handlers) {
        if (handlers == null || handlers.isEmpty()) {
            throw new IllegalArgumentException("At least one objective handler is required");
        }
        Map<String, AgentObjectiveHandler> copy = new LinkedHashMap<>();
        handlers.forEach((type, handler) -> {
            if (type == null || type.isBlank() || handler == null) {
                throw new IllegalArgumentException("Objective type and handler are required");
            }
            String normalized = type.trim();
            if (copy.putIfAbsent(normalized, handler) != null) {
                throw new IllegalArgumentException("Duplicate objective handler for " + normalized);
            }
        });
        this.handlers = Map.copyOf(copy);
    }

    public Optional<AgentObjectiveHandler> handlerFor(String objectiveType) {
        return Optional.ofNullable(handlers.get(objectiveType));
    }

    public AgentObjectiveAttachment reconcileAndAttach(AgentRuntimeEntry entry,
                                                        Character agent,
                                                        AgentObjectiveDefinition objective,
                                                        long nowMs) throws Exception {
        AgentObjectiveHandler handler = handlers.get(objective.type());
        if (handler == null) {
            throw new UnsupportedOperationException("No durable objective handler for " + objective.type());
        }
        return handler.reconcileAndAttach(entry, agent, objective, nowMs);
    }

    public Map<String, AgentObjectiveHandler> handlers() {
        return handlers;
    }
}
