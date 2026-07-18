package server.agents.runtime;

public enum AgentLifecyclePhase {
    CREATED,
    LOADING,
    ACTIVE,
    QUIESCING,
    SUSPENDED,
    RELOGIN_BACKOFF,
    QUARANTINED,
    STOPPING,
    OFFLINE,
    FAILED
}
