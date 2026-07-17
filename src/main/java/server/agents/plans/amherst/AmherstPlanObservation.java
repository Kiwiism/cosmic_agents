package server.agents.plans.amherst;

import server.agents.capabilities.AgentCapabilityStatus;
import server.agents.capabilities.runtime.AgentCapabilityJournalEvent;

public record AmherstPlanObservation(
        Type type,
        long timestampMs,
        String objectiveId,
        AmherstPlanObjectiveKind objectiveKind,
        AgentCapabilityStatus status,
        AgentCapabilityJournalEvent capabilityEvent,
        String message) {
    public enum Type {
        PLAN_STARTED,
        OBJECTIVE_STARTED,
        OBJECTIVE_FINISHED,
        CAPABILITY_EVENT,
        PLAN_COMPLETED,
        PLAN_ERROR
    }

    public AmherstPlanObservation {
        objectiveId = objectiveId == null ? "" : objectiveId;
        message = message == null ? "" : message;
    }
}
