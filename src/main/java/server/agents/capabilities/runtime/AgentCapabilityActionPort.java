package server.agents.capabilities.runtime;

@FunctionalInterface
public interface AgentCapabilityActionPort {
    AgentCapabilityActionSubmission submit(AgentCapabilityActionRequest request);
}
