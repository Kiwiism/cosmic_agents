package server.agents.runtime.activity.control;

import server.agents.runtime.activity.world.AgentWorldDirectorMode;
import server.agents.runtime.state.AgentCapabilityStateKey;

/** Session-local copy of persisted Director authority; no store access occurs on a tick. */
public final class AgentWorldDirectorRuntimeState {
    public static final AgentCapabilityStateKey<AgentWorldDirectorRuntimeState> STATE_KEY =
            new AgentCapabilityStateKey<>("runtime.world-director-mode",
                    AgentWorldDirectorRuntimeState.class, AgentWorldDirectorRuntimeState::new);

    private AgentWorldDirectorMode mode = AgentWorldDirectorMode.DISABLED;
    private long loadedAtMs;
    private String reason = "";

    public synchronized void restore(
            AgentWorldDirectorMode nextMode, String nextReason, long nowMs) {
        mode = nextMode == null ? AgentWorldDirectorMode.DISABLED : nextMode;
        loadedAtMs = Math.max(0L, nowMs);
        reason = nextReason == null ? "" : nextReason.trim();
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(mode, loadedAtMs, reason);
    }

    public record Snapshot(AgentWorldDirectorMode mode, long loadedAtMs, String reason) { }
}
