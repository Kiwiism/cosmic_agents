package server.agents.runtime.activity.world;

/** Operator or policy requests accepted by the World Director control plane. */
public enum AgentWorldDirectiveType {
    SET_MODE,
    START_ACTIVITY,
    STOP_ACTIVITY,
    TRANSFER_ACTIVITY,
    PAUSE,
    RESUME
}
