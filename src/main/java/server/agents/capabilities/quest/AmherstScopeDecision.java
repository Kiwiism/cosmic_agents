package server.agents.capabilities.quest;

import server.agents.capabilities.AgentCapabilityStatus;

public record AmherstScopeDecision(boolean allowed, AgentCapabilityStatus status, String reason) {
    public static AmherstScopeDecision allow() {
        return new AmherstScopeDecision(true, AgentCapabilityStatus.SUCCESS, "allowed");
    }

    public static AmherstScopeDecision block(AgentCapabilityStatus status, String reason) {
        return new AmherstScopeDecision(false, status, reason);
    }
}
