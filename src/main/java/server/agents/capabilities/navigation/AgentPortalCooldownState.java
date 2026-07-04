package server.agents.capabilities.navigation;

/**
 * Portal-use cooldown gate for live Agent navigation.
 */
public final class AgentPortalCooldownState {
    private long useCooldownUntilMs = 0L;

    public long useCooldownUntilMs() {
        return useCooldownUntilMs;
    }

    public void setUseCooldownUntilMs(long useCooldownUntilMs) {
        this.useCooldownUntilMs = useCooldownUntilMs;
    }

    public boolean onCooldown(long nowMs) {
        return nowMs < useCooldownUntilMs;
    }
}
