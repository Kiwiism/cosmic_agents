package server.agents.runtime.scheduler;

/** Declares why a live Agent session needs a strong mutation barrier. */
public enum AgentQuiescenceReason {
    PROFILE_EXCHANGE,
    CHARACTER_TRANSFER,
    CONSISTENT_SNAPSHOT,
    RELEASE,
    SHUTDOWN,
    MAINTENANCE
}
