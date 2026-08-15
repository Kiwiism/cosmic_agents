package server.agents.runtime.townlife;

import server.agents.capabilities.townlife.AgentTownLifeSessionHandle;
import server.agents.runtime.state.AgentCapabilityStateKey;

/** External scheduling state; TownLife does not interpret why this deadline exists. */
public final class AgentTownLifeVisitLeaseState {
    public static final AgentCapabilityStateKey<AgentTownLifeVisitLeaseState> STATE_KEY =
            new AgentCapabilityStateKey<>(
                    "town-life.visit-lease", AgentTownLifeVisitLeaseState.class,
                    AgentTownLifeVisitLeaseState::new);

    private AgentTownLifeSessionHandle handle;
    private long exitAtMs;
    private long gracefulTimeoutMs;
    private String exitReason = "";

    synchronized void start(
            AgentTownLifeSessionHandle nextHandle,
            long nextExitAtMs,
            long nextGracefulTimeoutMs,
            String nextExitReason) {
        handle = nextHandle;
        exitAtMs = nextExitAtMs;
        gracefulTimeoutMs = nextGracefulTimeoutMs;
        exitReason = nextExitReason == null ? "" : nextExitReason.trim();
    }

    public synchronized boolean active() {
        return handle != null;
    }

    public synchronized AgentTownLifeSessionHandle handle() {
        return handle;
    }

    public synchronized long exitAtMs() {
        return exitAtMs;
    }

    public synchronized long gracefulTimeoutMs() {
        return gracefulTimeoutMs;
    }

    public synchronized String exitReason() {
        return exitReason;
    }

    synchronized void clear() {
        handle = null;
        exitAtMs = 0L;
        gracefulTimeoutMs = 0L;
        exitReason = "";
    }
}
