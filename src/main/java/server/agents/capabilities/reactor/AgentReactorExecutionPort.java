package server.agents.capabilities.reactor;

public interface AgentReactorExecutionPort {
    AgentReactorInteractionResult execute(AgentReactorInteractionRequest request, AgentReactorTarget target);
}
