package server.agents.integration;

/** Thread-affinity contract for Agent access to authoritative server state. */
public enum AgentGatewayThreadAffinity {
    SHARD_SAFE_DIRECT,
    SERVER_EXECUTOR_REQUIRED,
    READ_ONLY_SNAPSHOT,
    ASYNC_EXTERNAL,
    UNSAFE_PENDING_REFACTOR
}
