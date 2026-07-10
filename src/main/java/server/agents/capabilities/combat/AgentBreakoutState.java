package server.agents.capabilities.combat;

/**
 * Surround-breakout commitment used to avoid reversing escape direction.
 */
public final class AgentBreakoutState {
    private int direction;
    private long untilMs;

    public boolean hasCommitment() {
        return direction != 0;
    }

    public int direction() {
        return direction;
    }

    public long untilMs() {
        return untilMs;
    }

    public boolean expired(long nowMs) {
        return nowMs >= untilMs;
    }

    public void setCommitment(int direction, long untilMs) {
        this.direction = direction;
        this.untilMs = untilMs;
    }

    public void clear() {
        direction = 0;
        untilMs = 0L;
    }
}
