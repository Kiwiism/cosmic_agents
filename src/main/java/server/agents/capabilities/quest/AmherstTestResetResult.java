package server.agents.capabilities.quest;

import server.agents.capabilities.AgentCapabilityStatus;

public record AmherstTestResetResult(boolean allowed, AgentCapabilityStatus status, String message) {
    public static AmherstTestResetResult allowed(String message) {
        return new AmherstTestResetResult(true, AgentCapabilityStatus.SUCCESS, message);
    }

    public static AmherstTestResetResult blocked(AgentCapabilityStatus status, String message) {
        return new AmherstTestResetResult(false, status, message);
    }
}
