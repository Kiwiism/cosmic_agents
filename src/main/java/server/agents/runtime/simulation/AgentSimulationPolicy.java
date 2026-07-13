package server.agents.runtime.simulation;

import server.agents.runtime.AgentRuntimeEntry;

@FunctionalInterface
public interface AgentSimulationPolicy {
    AgentSimulationMode selectMode(AgentRuntimeEntry entry);
}
