package server.agents.operations.events;

import client.Character;
import server.agents.events.AgentEvent;
import server.agents.events.AgentEventPriority;
import server.agents.objectives.AgentObjectiveDefinition;
import server.agents.objectives.AgentObjectiveKernel;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSessionEventRuntime;

/** Resolves Agent session context and publishes one operational fact. */
public final class AgentOperationalEventPublisher {
    private AgentOperationalEventPublisher() {
    }

    public static void publish(AgentRuntimeEntry entry, EventFactory factory,
                               AgentEventPriority priority) {
        Character agent = entry == null ? null : entry.bot();
        if (agent == null || agent.getId() <= 0 || factory == null) {
            return;
        }
        AgentObjectiveDefinition objective = AgentObjectiveKernel.active(entry);
        String objectiveId = objective == null ? "" : objective.objectiveId();
        AgentSessionEventRuntime.bus(entry).publish(factory.create(objectiveId), priority);
    }

    public static void publishFor(Character agent, EventFactory factory,
                                  AgentEventPriority priority) {
        if (agent == null || agent.getId() <= 0) {
            return;
        }
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterInstance(agent);
        if (entry != null) {
            publish(entry, factory, priority);
        }
    }

    @FunctionalInterface
    public interface EventFactory {
        AgentEvent create(String objectiveId);
    }
}
