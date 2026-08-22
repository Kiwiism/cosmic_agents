package server.agents.runtime.activity.world;

/** How an owning activity must yield to a directive. */
public enum AgentWorldInterruptionPolicy {
    WAIT_FOR_SAFE_BOUNDARY,
    AFTER_ACTIVITY,
    EMERGENCY_STOP
}
