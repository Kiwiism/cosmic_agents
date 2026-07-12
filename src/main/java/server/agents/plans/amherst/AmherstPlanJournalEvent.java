package server.agents.plans.amherst;

public record AmherstPlanJournalEvent(
        long timestampMs,
        AmherstPlanJournalEventType type,
        String objectiveId,
        String reasonCode,
        String message) {

    public AmherstPlanJournalEvent {
        reasonCode = reasonCode == null ? "" : reasonCode;
        message = message == null ? "" : message;
    }
}
