package server.agents.runtime.field;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** External timing state; the field activity remains responsible only for local lifecycle. */
public final class AgentFieldVisitLeaseState {
    public static final AgentCapabilityStateKey<AgentFieldVisitLeaseState> STATE_KEY =
            new AgentCapabilityStateKey<>("field.visit-lease",
                    AgentFieldVisitLeaseState.class, AgentFieldVisitLeaseState::new);

    private AgentFieldSessionHandle handle;
    private long exitAtMs;
    private long gracefulTimeoutMs;
    private String exitReason = "";
    private boolean exitRequested;

    synchronized void start(AgentFieldSessionHandle nextHandle, long nextExitAtMs,
                            long nextGracefulTimeoutMs, String nextExitReason) {
        handle = nextHandle;
        exitAtMs = nextExitAtMs;
        gracefulTimeoutMs = nextGracefulTimeoutMs;
        exitReason = nextExitReason == null ? "" : nextExitReason.trim();
        exitRequested = false;
    }

    public synchronized boolean active() { return handle != null; }
    public synchronized AgentFieldSessionHandle handle() { return handle; }
    public synchronized long exitAtMs() { return exitAtMs; }
    public synchronized long gracefulTimeoutMs() { return gracefulTimeoutMs; }
    public synchronized String exitReason() { return exitReason; }
    public synchronized boolean exitRequested() { return exitRequested; }
    synchronized void markExitRequested() { exitRequested = true; }

    synchronized void clear() {
        handle = null;
        exitAtMs = 0L;
        gracefulTimeoutMs = 0L;
        exitReason = "";
        exitRequested = false;
    }
}
