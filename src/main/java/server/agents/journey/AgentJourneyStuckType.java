package server.agents.journey;

/** Diagnostic classifications; detection never performs gameplay mutation. */
public enum AgentJourneyStuckType {
    PLAN_BLOCKED,
    SEMANTIC_PROGRESS_STALL,
    MAP_DWELL,
    NAVIGATION_LOOP,
    POSITION_OSCILLATION,
    RECOVERY_THRASH
}
