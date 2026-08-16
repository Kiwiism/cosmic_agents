package server.agents.plans;

public record AgentPlanExitResult(Status status, String reason) {
    public AgentPlanExitResult {
        status = status == null ? Status.REJECTED_INVALID_REQUEST : status;
        reason = reason == null ? "" : reason.trim();
    }

    public enum Status {
        REQUESTED,
        SUSPENDED,
        EXITED,
        NOT_ACTIVE,
        REJECTED_NOT_OWNER,
        REJECTED_INVALID_REQUEST
    }
}
