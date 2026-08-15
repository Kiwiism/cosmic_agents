package server.agents.capabilities.townlife;

/** Terminal/non-terminal result of the currently committed local activity. */
public enum AgentTownLifeActivityResult {
    NONE(false),
    ACTIVE(false),
    COMPLETED(true),
    ABANDONED(true),
    FAILED(true),
    TIMED_OUT(true),
    CANCELLED(true);

    private final boolean terminal;

    AgentTownLifeActivityResult(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean terminal() {
        return terminal;
    }
}
