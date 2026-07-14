package server.agents.runtime.scheduler;

public enum AgentLoadSheddingLevel {
    NORMAL,
    SUPPRESS_COSMETIC,
    REDUCE_BACKGROUND_CADENCE,
    PAUSE_DEFERRED_AND_LLM,
    PAUSE_LOW_PRIORITY_BACKGROUND,
    ADMISSION_CONTROL;

    public boolean atLeast(AgentLoadSheddingLevel other) {
        return ordinal() >= other.ordinal();
    }

    AgentLoadSheddingLevel recoverOneLevel() {
        return this == NORMAL ? NORMAL : values()[ordinal() - 1];
    }
}
