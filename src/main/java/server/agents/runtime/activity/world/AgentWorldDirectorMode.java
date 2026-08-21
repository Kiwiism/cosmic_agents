package server.agents.runtime.activity.world;

/** CONTROLLED and AUTONOMOUS are reserved for the later opt-in live rollout. */
public enum AgentWorldDirectorMode {
    DISABLED,
    SHADOW,
    CONTROLLED,
    AUTONOMOUS
}
