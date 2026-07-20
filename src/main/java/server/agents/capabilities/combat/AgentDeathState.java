package server.agents.capabilities.combat;

/**
 * Mutable death/respawn window state for one live Agent.
 */
public final class AgentDeathState {
    private long deadSinceMs = 0L;
    private long deadUntilMs = 0L;

    public long deadSinceMs() {
        return deadSinceMs;
    }

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
        deadSinceMs = nowMs;
        deadUntilMs = nowMs + deadDurationMs;
    }

    public void setDeadUntilMs(long deadUntilMs) {
        this.deadUntilMs = deadUntilMs;
    }

    public void clear() {
        deadSinceMs = 0L;
        deadUntilMs = 0L;
    }
}
