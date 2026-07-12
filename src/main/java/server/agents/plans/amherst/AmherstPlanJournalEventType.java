package server.agents.plans.amherst;

public enum AmherstPlanJournalEventType {
    OBJECTIVE_STARTED,
    CHILD_HANDOFF,
    CHILD_RESULT,
    RETRY,
    RECONCILED_SATISFIED,
    RECONCILED_REOPENED,
    BLOCKED,
    CANCELLED,
    OBJECTIVE_TERMINAL,
    PLAN_COMPLETED
}
