package server.agents.runtime;

public final class AgentLifecycleState {
    private volatile AgentLifecyclePhase phase = AgentLifecyclePhase.ACTIVE;
    private volatile String reason = "registered";
    private volatile long changedAtMs = System.currentTimeMillis();
    private volatile long sequence;

    public AgentLifecyclePhase phase() {
        return phase;
    }

    public String reason() {
        return reason;
    }

    public long changedAtMs() { return changedAtMs; }

    public long sequence() { return sequence; }

    public synchronized void transition(AgentLifecyclePhase phase, String reason) {
        if (phase == null) {
            throw new IllegalArgumentException("Lifecycle phase is required");
        }
        this.phase = phase;
        this.reason = reason == null ? "" : reason;
        this.changedAtMs = System.currentTimeMillis();
        this.sequence++;
    }
}
