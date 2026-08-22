package server.agents.runtime.activity.world;

/** What the Director should request after the directed activity terminates. */
public enum AgentWorldCompletionPolicy {
    REQUEST_NEXT_DECISION,
    RETURN_TO_PREVIOUS_ACTIVITY,
    HOLD_POSITION
}
