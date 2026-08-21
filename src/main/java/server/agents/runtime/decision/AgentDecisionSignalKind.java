package server.agents.runtime.decision;

/** Observable facts that policies may consider without performing an action. */
public enum AgentDecisionSignalKind {
    PROGRESS_OBSERVED,
    RELEVANT_DAMAGE_OBSERVED,
    OBJECTIVE_ADVANCED,
    LEGITIMATE_WAIT,
    NAVIGATION_PROGRESS,
    NAVIGATION_BLOCKED,
    TARGET_UNAVAILABLE,
    RESOURCE_PRESSURE,
    INVENTORY_BLOCKED,
    ADMISSION_UNAVAILABLE,
    RETRY_EXHAUSTED,
    RECOVERABLE_FAILURE,
    TERMINAL_FAILURE
}
