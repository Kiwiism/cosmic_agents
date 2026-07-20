package server.agents.resources.events;

import client.Character;
import server.agents.events.AgentEvent;
import server.agents.events.AgentEventPriority;
import server.agents.objectives.AgentObjectiveDefinition;
import server.agents.objectives.AgentObjectiveKernel;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSessionEventRuntime;

/** Resolves a live Agent session and publishes one portable resource fact. */
public final class AgentResourceEventPublisher {
    private AgentResourceEventPublisher() {
    }

    public static void publish(AgentRuntimeEntry entry, AgentEvent event, AgentEventPriority priority) {
        if (entry != null && event != null) {
            AgentSessionEventRuntime.bus(entry).publish(event, priority);
        }
    }

    public static void publishFor(Character agent, EventFactory factory, AgentEventPriority priority) {
        if (agent == null || agent.getId() <= 0 || factory == null) {
            return;
        }
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterInstance(agent);
        if (entry == null) {
            return;
        }
        AgentObjectiveDefinition objective = AgentObjectiveKernel.active(entry);
        String objectiveId = objective == null ? "" : objective.objectiveId();
        publish(entry, factory.create(objectiveId), priority);
    }

    @FunctionalInterface
    public interface EventFactory {
        AgentEvent create(String objectiveId);
    }
}
