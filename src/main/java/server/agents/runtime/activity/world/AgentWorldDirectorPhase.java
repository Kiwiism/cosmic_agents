package server.agents.runtime.activity.world;

/** Preparation persists only DISABLED, OBSERVING, and PAUSED sessions. */
public enum AgentWorldDirectorPhase {
    DISABLED,
    OBSERVING,
    EVALUATING,
    STARTING,
    RUNNING,
    HANDOFF,
    WAITING,
    GOAL_COMPLETE,
    PAUSED,
    FAILED
}
