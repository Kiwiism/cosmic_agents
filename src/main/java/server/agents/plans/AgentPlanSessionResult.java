package server.agents.plans;

/** Typed top-level plan admission result. */
public record AgentPlanSessionResult(Status status, AgentPlanSessionHandle handle, String reason) {
    public AgentPlanSessionResult {
        status = status == null ? Status.REJECTED_INVALID_REQUEST : status;
        reason = reason == null ? "" : reason.trim();
        if ((status == Status.STARTED || status == Status.ALREADY_ACTIVE_SAME_REQUEST)
                && handle == null) {
            throw new IllegalArgumentException("successful plan admission requires a handle");
        }
    }

    public boolean started() {
        return status == Status.STARTED || status == Status.ALREADY_ACTIVE_SAME_REQUEST;
    }

    public enum Status {
        STARTED,
        ALREADY_ACTIVE_SAME_REQUEST,
        REJECTED_ALREADY_ACTIVE,
        REJECTED_FOREGROUND_BUSY,
        REJECTED_PLAN,
        REJECTED_INVALID_REQUEST
    }
}
