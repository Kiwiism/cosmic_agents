package server.agents.runtime.activity;

/**
 * Result of one foreground activity tick.
 *
 * <p>{@link #PASS} releases the arbiter to the next lower-priority activity.
 * The other results retain foreground ownership for this tick, while only
 * {@link #CONSUMED} reports that the Agent used the tick.</p>
 */
public enum AgentForegroundActivityTick {
    CONSUMED(true, true),
    IDLE(true, false),
    PASS(false, false);

    private final boolean ownsForeground;
    private final boolean consumedTick;

    AgentForegroundActivityTick(boolean ownsForeground, boolean consumedTick) {
        this.ownsForeground = ownsForeground;
        this.consumedTick = consumedTick;
    }

    public boolean ownsForeground() {
        return ownsForeground;
    }

    public boolean consumedTick() {
        return consumedTick;
    }
}
