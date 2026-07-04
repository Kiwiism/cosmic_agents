package server.agents.capabilities.movement;

/**
 * Mutable down-jump intent and grace-period state for one live Agent runtime.
 */
public final class AgentDownJumpState {
    private boolean pending;
    private long gracePeriodMs;

    public boolean pending() {
        return pending;
    }

    public void setPending(boolean pending) {
        this.pending = pending;
    }

    public long gracePeriodMs() {
        return gracePeriodMs;
    }

    public void setGracePeriodMs(long gracePeriodMs) {
        this.gracePeriodMs = gracePeriodMs;
    }

    public boolean hasGracePeriod() {
        return gracePeriodMs != 0L;
    }

    public void clear() {
        pending = false;
        gracePeriodMs = 0L;
    }
}
