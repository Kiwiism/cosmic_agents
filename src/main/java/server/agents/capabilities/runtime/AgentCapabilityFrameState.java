package server.agents.capabilities.runtime;

public enum AgentCapabilityFrameState {
    STARTING,
    RUNNING,
    WAITING_CHILD,
    SUCCEEDED,
    BLOCKED,
    FAILED,
    CANCELLED,
    TIMED_OUT
}
