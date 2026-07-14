package server.agents.runtime.scheduler;

@FunctionalInterface
public interface AgentServerHealthProbe {
    AgentServerHealthSnapshot sample();
}
