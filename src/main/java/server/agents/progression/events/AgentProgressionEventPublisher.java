package server.agents.progression.events;

import client.Character;
import server.agents.events.AgentEvent;
import server.agents.events.AgentEventPriority;
import server.agents.objectives.AgentObjectiveDefinition;
import server.agents.objectives.AgentObjectiveKernel;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSessionEventRuntime;

/** Authoritative publication boundary for progression facts. */
public final class AgentProgressionEventPublisher {
    private AgentProgressionEventPublisher() {
    }

    public static void publish(AgentRuntimeEntry entry, AgentEvent event, AgentEventPriority priority) {
        if (entry != null && event != null) {
            AgentSessionEventRuntime.bus(entry).publish(event, priority);
        }
    }

    public static void publishFor(Character agent, AgentEventFactory factory, AgentEventPriority priority) {
        if (agent == null || factory == null) {
            return;
        }
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(agent.getId());
        if (entry != null) {
            publish(entry, factory.create(entry, objectiveId(entry)), priority);
        }
    }

    public static String objectiveId(AgentRuntimeEntry entry) {
        if (entry == null) {
            return "";
        }
        AgentObjectiveDefinition objective = AgentObjectiveKernel.active(entry);
        return objective == null ? "" : objective.objectiveId();
    }

    @FunctionalInterface
    public interface AgentEventFactory {
        AgentEvent create(AgentRuntimeEntry entry, String objectiveId);
    }
}
