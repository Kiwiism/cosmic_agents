package server.agents.runtime.field;

public record AgentFieldSessionResult(
        Status status,
        AgentFieldSessionHandle handle,
        String reason) {
    public AgentFieldSessionResult {
        status = status == null ? Status.REJECTED_INVALID_REQUEST : status;
        reason = reason == null ? "" : reason.trim();
    }

    public boolean started() {
        return status == Status.STARTED;
    }

    public enum Status {
        STARTED,
        ALREADY_ACTIVE_SAME_REQUEST,
        REJECTED_ALREADY_ACTIVE,
        REJECTED_INVALID_REQUEST,
        REJECTED_WRONG_MAP,
        REJECTED_NO_SESSION,
        REJECTED_CAPACITY,
        REJECTED_FOREGROUND_BUSY
    }
}
