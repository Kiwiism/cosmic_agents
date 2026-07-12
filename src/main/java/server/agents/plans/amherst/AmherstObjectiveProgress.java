package server.agents.plans.amherst;

public record AmherstObjectiveProgress(
        String objectiveId,
        AmherstObjectiveProgressStatus status,
        int attempts,
        String reasonCode,
        String message,
        long startedAtMs,
        long updatedAtMs,
        long completedAtMs,
        int capabilityJournalStart,
        int capabilityJournalEnd) {

    public AmherstObjectiveProgress {
        status = status == null ? AmherstObjectiveProgressStatus.PENDING : status;
        reasonCode = reasonCode == null ? "" : reasonCode;
        message = message == null ? "" : message;
    }

    public static AmherstObjectiveProgress pending(String objectiveId) {
        return new AmherstObjectiveProgress(objectiveId, AmherstObjectiveProgressStatus.PENDING,
                0, "", "", 0L, 0L, 0L, -1, -1);
    }
}
