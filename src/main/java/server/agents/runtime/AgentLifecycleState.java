package server.agents.runtime;

public final class AgentLifecycleState {
    private volatile AgentLifecyclePhase phase = AgentLifecyclePhase.ACTIVE;
    private volatile String reason;

    public AgentLifecyclePhase phase() {
        return phase;
    }

    public String reason() {
        return reason;
    }

    public void transition(AgentLifecyclePhase phase, String reason) {
        this.phase = phase;
        this.reason = reason;
    }
}
