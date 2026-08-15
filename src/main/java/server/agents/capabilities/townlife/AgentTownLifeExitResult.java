package server.agents.capabilities.townlife;

public record AgentTownLifeExitResult(
        Status status,
        String sessionId,
        String reason) {

    public AgentTownLifeExitResult {
        status = status == null ? Status.REJECTED_INVALID_REQUEST : status;
        sessionId = sessionId == null ? "" : sessionId.trim();
        reason = reason == null ? "" : reason.trim();
    }

    public boolean accepted() {
        return status == Status.EXIT_REQUESTED
                || status == Status.ALREADY_DRAINING
                || status == Status.EXITED
                || status == Status.FORCED;
    }

    public enum Status {
        EXIT_REQUESTED,
        ALREADY_DRAINING,
        EXITED,
        FORCED,
        NOT_ACTIVE,
        REJECTED_STALE_SESSION,
        REJECTED_CALLER_MISMATCH,
        REJECTED_INVALID_REQUEST
    }
}
