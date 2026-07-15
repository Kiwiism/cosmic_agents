package server.agents.profiles;

public final class AgentBehaviorProfileState {
    private AgentBehaviorProfile profile;
    private long nextNavigationFidgetAtMs;

    public AgentBehaviorProfile profile() {
        return profile;
    }

    public void assign(AgentBehaviorProfile profile) {
        this.profile = profile;
        nextNavigationFidgetAtMs = 0L;
    }

    public long nextNavigationFidgetAtMs() {
        return nextNavigationFidgetAtMs;
    }

    public void setNextNavigationFidgetAtMs(long nextNavigationFidgetAtMs) {
        this.nextNavigationFidgetAtMs = Math.max(0L, nextNavigationFidgetAtMs);
    }
}
