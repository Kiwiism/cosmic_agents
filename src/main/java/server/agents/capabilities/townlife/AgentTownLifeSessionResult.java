package server.agents.capabilities.townlife;

/** Typed lifecycle outcome; callers retain ownership of travel, quests, shops, and recovery. */
public record AgentTownLifeSessionResult(Status status,
                                         int townMapId,
                                         String reason,
                                         AgentTownLifeSessionHandle handle) {
    public AgentTownLifeSessionResult(Status status, int townMapId, String reason) {
        this(status, townMapId, reason, null);
    }

    public AgentTownLifeSessionResult {
        status = status == null ? Status.REJECTED_INVALID_REQUEST : status;
        reason = reason == null ? "" : reason;
    }

    public boolean started() {
        return status == Status.STARTED
                || status == Status.ALREADY_ACTIVE
                || status == Status.ALREADY_ACTIVE_SAME_REQUEST;
    }

    public enum Status {
        STARTED,
        ALREADY_ACTIVE,
        ALREADY_ACTIVE_SAME_REQUEST,
        REJECTED_ALREADY_ACTIVE_OTHER_REQUEST,
        STOPPED,
        NOT_ACTIVE,
        REJECTED_DISABLED,
        REJECTED_NOT_LOCAL,
        REJECTED_CAPACITY,
        REJECTED_UNSUPPORTED_TOWN,
        REJECTED_INVALID_REQUEST
    }
}
