package server.agents.runtime.mailbox;

/** Classifies whether mailbox work may run while a session is quiescing. */
public enum AgentMailboxWorkClass {
    ORDINARY(false),
    COMPLETION_CRITICAL(true),
    LIFECYCLE_CRITICAL(true);

    private final boolean quiescenceCritical;

    AgentMailboxWorkClass(boolean quiescenceCritical) {
        this.quiescenceCritical = quiescenceCritical;
    }

    public boolean quiescenceCritical() {
        return quiescenceCritical;
    }
}
