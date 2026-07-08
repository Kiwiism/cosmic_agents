package server.agents.capabilities.reactor;

import server.agents.capabilities.AgentCapabilityStatus;

public record AgentReactorInteractionResult(
        boolean success,
        AgentCapabilityStatus status,
        String message,
        AgentReactorTarget target) {

    public static AgentReactorInteractionResult success(String message, AgentReactorTarget target) {
        return new AgentReactorInteractionResult(true, AgentCapabilityStatus.SUCCESS, message, target);
    }

    public static AgentReactorInteractionResult blocked(AgentCapabilityStatus status, String message) {
        return new AgentReactorInteractionResult(false, status, message, null);
    }

    public static AgentReactorInteractionResult pending(AgentCapabilityStatus status, String message,
            AgentReactorTarget target) {
        return new AgentReactorInteractionResult(false, status, message, target);
    }
}
