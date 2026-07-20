package server.agents.objectives;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

/** Reconciles one durable objective type and recreates any transient execution state it needs. */
@FunctionalInterface
public interface AgentObjectiveHandler {
    AgentObjectiveAttachment reconcileAndAttach(AgentRuntimeEntry entry,
                                                 Character agent,
                                                 AgentObjectiveDefinition objective,
                                                 long nowMs) throws Exception;
}
