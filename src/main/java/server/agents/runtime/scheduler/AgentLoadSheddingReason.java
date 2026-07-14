package server.agents.runtime.scheduler;

public enum AgentLoadSheddingReason {
    QUEUE_LAG,
    READY_BACKLOG,
    INGRESS_PRESSURE,
    PROCESS_CPU,
    HEAP_PRESSURE,
    GC_PAUSE,
    PLAYER_PATH_UNHEALTHY,
    RECOVERY_HYSTERESIS,
    POPULATION_LIMIT
}
