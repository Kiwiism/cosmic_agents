package server.agents.capabilities.runtime;

import server.agents.capabilities.AgentCapabilityStatus;

public record AgentCapabilityResult(
        AgentCapabilityStatus status,
        AgentCapabilityReasonCode reasonCode,
        String message,
        AgentCapabilityOutput output) {

    public AgentCapabilityResult(AgentCapabilityStatus status,
                                 AgentCapabilityReasonCode reasonCode,
                                 String message) {
        this(status, reasonCode, message, null);
    }

    public AgentCapabilityResult {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        reasonCode = reasonCode == null ? AgentCapabilityReasonCode.NONE : reasonCode;
        message = message == null ? "" : message;
    }

    public boolean terminal() {
        return switch (status) {
            case SUCCESS, MISSING_REQUIREMENT, BLOCKED_BY_SCOPE,
                    BLOCKED_FORBIDDEN_QUEST, BLOCKED_FORBIDDEN_MAP,
                    BLOCKED_FORBIDDEN_NPC, CANCELLED, TIMED_OUT, FAILED -> true;
            default -> false;
        };
    }

    public static AgentCapabilityResult success(String message) {
        return new AgentCapabilityResult(AgentCapabilityStatus.SUCCESS,
                AgentCapabilityReasonCode.NONE, message);
    }

    public static AgentCapabilityResult success(String message, AgentCapabilityOutput output) {
        return new AgentCapabilityResult(AgentCapabilityStatus.SUCCESS,
                AgentCapabilityReasonCode.NONE, message, output);
    }

    public static AgentCapabilityResult failed(AgentCapabilityReasonCode reasonCode, String message) {
        return new AgentCapabilityResult(AgentCapabilityStatus.FAILED, reasonCode, message);
    }
}
