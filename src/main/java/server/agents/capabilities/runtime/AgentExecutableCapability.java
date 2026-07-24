package server.agents.capabilities.runtime;

import server.agents.capabilities.AgentCapability;

import java.util.Set;

public interface AgentExecutableCapability<C extends AgentCapabilityCommand> extends AgentCapability {
    String id();

    AgentCapabilityStep tick(AgentCapabilityContext context, C command);

    default Set<AgentCapabilityResource> requiredResources(C command) {
        return AgentCapabilityResourcePolicy.defaultsFor(id());
    }

    default void onTerminal(AgentCapabilityContext context, C command, AgentCapabilityResult result) {
    }
}
