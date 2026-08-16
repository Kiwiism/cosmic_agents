package server.agents.runtime.activity;

/** Result of advancing one Activity Host controller. */
public enum AgentActivityTick {
    CONSUMED(true, true),
    IDLE(true, false),
    PASS(false, false);

    private final boolean ownsExecution;
    private final boolean consumedTick;

    AgentActivityTick(boolean ownsExecution, boolean consumedTick) {
        this.ownsExecution = ownsExecution;
        this.consumedTick = consumedTick;
    }

    public boolean ownsExecution() {
        return ownsExecution;
    }

    public boolean consumedTick() {
        return consumedTick;
    }
}
