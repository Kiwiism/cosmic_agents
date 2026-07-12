package server.agents.capabilities.runtime;

import server.agents.capabilities.AgentCapabilityStatus;

public record AgentCapabilityStep(
        AgentCapabilityStatus status,
        AgentCapabilityResult result,
        AgentCapabilityInvocation<?> child,
        boolean consumedTick) {

    public AgentCapabilityStep {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (status == AgentCapabilityStatus.WAITING_CHILD && child == null) {
            throw new IllegalArgumentException("child invocation is required for handoff");
        }
        if (isTerminalStatus(status) && (result == null || !result.terminal())) {
            throw new IllegalArgumentException("terminal step requires a terminal result");
        }
    }

    public static AgentCapabilityStep running(String message) {
        return running(message, true);
    }

    public static AgentCapabilityStep running(String message, boolean consumedTick) {
        return new AgentCapabilityStep(AgentCapabilityStatus.RUNNING,
                new AgentCapabilityResult(AgentCapabilityStatus.RUNNING,
                        AgentCapabilityReasonCode.IN_PROGRESS, message), null, consumedTick);
    }

    public static AgentCapabilityStep retry(String message) {
        return new AgentCapabilityStep(AgentCapabilityStatus.RETRY,
                new AgentCapabilityResult(AgentCapabilityStatus.RETRY,
                        AgentCapabilityReasonCode.RETRY_REQUESTED, message), null, true);
    }

    public static AgentCapabilityStep handoff(AgentCapabilityInvocation<?> child, String message) {
        return new AgentCapabilityStep(AgentCapabilityStatus.WAITING_CHILD,
                new AgentCapabilityResult(AgentCapabilityStatus.WAITING_CHILD,
                        AgentCapabilityReasonCode.CHILD_REQUIRED, message), child, true);
    }

    public static AgentCapabilityStep terminal(AgentCapabilityResult result) {
        return new AgentCapabilityStep(result.status(), result, null, true);
    }

    private static boolean isTerminalStatus(AgentCapabilityStatus status) {
        return switch (status) {
            case SUCCESS, MISSING_REQUIREMENT, BLOCKED_BY_SCOPE,
                    BLOCKED_FORBIDDEN_QUEST, BLOCKED_FORBIDDEN_MAP,
                    BLOCKED_FORBIDDEN_NPC, CANCELLED, TIMED_OUT, FAILED -> true;
            default -> false;
        };
    }
}
