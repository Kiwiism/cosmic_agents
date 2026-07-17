package server.agents.runtime;

public enum AgentLifecyclePhase {
    ACTIVE,
    SUSPENDED,
    RELOGIN_BACKOFF,
    QUARANTINED,
    STOPPING,
    OFFLINE
}
