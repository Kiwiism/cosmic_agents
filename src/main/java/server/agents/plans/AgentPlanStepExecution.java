package server.agents.plans;

public record AgentPlanStepExecution(AgentPlanExecutionStatus status, boolean consumed, String reason) {
    public AgentPlanStepExecution {
        if (status == null || status == AgentPlanExecutionStatus.IDLE) {
            throw new IllegalArgumentException("A non-idle step status is required");
        }
        reason = reason == null ? "" : reason;
    }

    public static AgentPlanStepExecution active(boolean consumed) {
        return new AgentPlanStepExecution(AgentPlanExecutionStatus.ACTIVE, consumed, "");
    }

    public static AgentPlanStepExecution terminal(AgentPlanExecutionStatus status, String reason) {
        return new AgentPlanStepExecution(status, true, reason);
    }
}
