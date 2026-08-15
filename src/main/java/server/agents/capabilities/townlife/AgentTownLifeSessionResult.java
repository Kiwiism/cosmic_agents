package server.agents.capabilities.townlife;

/** Typed lifecycle outcome; callers retain ownership of travel, quests, shops, and recovery. */
public record AgentTownLifeSessionResult(Status status,
                                         int townMapId,
                                         String reason) {
    public AgentTownLifeSessionResult {
        status = status == null ? Status.REJECTED_INVALID_REQUEST : status;
        reason = reason == null ? "" : reason;
    }

    public boolean started() {
        return status == Status.STARTED || status == Status.ALREADY_ACTIVE;
    }

    public enum Status {
        STARTED,
        ALREADY_ACTIVE,
        STOPPED,
        NOT_ACTIVE,
        REJECTED_DISABLED,
        REJECTED_NOT_LOCAL,
        REJECTED_CAPACITY,
        REJECTED_UNSUPPORTED_TOWN,
        REJECTED_INVALID_REQUEST
    }
}
