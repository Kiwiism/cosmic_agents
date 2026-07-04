package server.agents.runtime;

/**
 * Mutable death/respawn window state for one live Agent.
 */
public final class AgentDeathState {
    private long deadUntilMs = 0L;

    public long deadUntilMs() {
        return deadUntilMs;
    }

    public boolean isDead() {
        return deadUntilMs > 0L;
    }

    public boolean shouldEnterDeadState(int hp) {
        return !isDead() && hp <= 0;
    }

    public boolean isRespawnDue(long nowMs) {
        return deadUntilMs > 0L && nowMs >= deadUntilMs;
    }

    public void enterDeadState(long nowMs, long deadDurationMs) {
        deadUntilMs = nowMs + deadDurationMs;
    }

    public void setDeadUntilMs(long deadUntilMs) {
        this.deadUntilMs = deadUntilMs;
    }

    public void clear() {
        deadUntilMs = 0L;
    }
}
