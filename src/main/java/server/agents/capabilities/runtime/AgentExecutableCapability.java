package server.agents.capabilities.runtime;

import server.agents.capabilities.AgentCapability;

public interface AgentExecutableCapability<C extends AgentCapabilityCommand> extends AgentCapability {
    String id();

    AgentCapabilityStep tick(AgentCapabilityContext context, C command);

    default void onTerminal(AgentCapabilityContext context, C command, AgentCapabilityResult result) {
    }
}
