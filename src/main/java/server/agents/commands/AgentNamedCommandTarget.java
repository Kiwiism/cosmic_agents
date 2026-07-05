package server.agents.commands;

import server.agents.runtime.AgentRuntimeHandle;

public record AgentNamedCommandTarget<E extends AgentRuntimeHandle>(E entry, String name) implements AgentCommandTarget {
}
