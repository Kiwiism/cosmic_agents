package server.agents.capabilities.runtime;

public enum AgentCapabilityReasonCode {
    NONE,
    IN_PROGRESS,
    CHILD_REQUIRED,
    RETRY_REQUESTED,
    RETRIES_EXHAUSTED,
    CANCELLED_BY_REQUEST,
    DEADLINE_EXCEEDED,
    MISSING_REQUIREMENT,
    BLOCKED_BY_SCOPE,
    LIVE_STATE_MISMATCH,
    EXECUTION_FAILED
}
