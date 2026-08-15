package server.agents.runtime.townlife;

import server.agents.capabilities.townlife.AgentTownLifeLifecycleEvent;
import server.agents.runtime.state.AgentCapabilityStateKey;

/** Last synchronous TownLife terminal result for exact external handoff. */
public final class AgentTownLifeTerminalState {
    public static final AgentCapabilityStateKey<AgentTownLifeTerminalState> STATE_KEY =
            new AgentCapabilityStateKey<>("town-life.last-terminal",
                    AgentTownLifeTerminalState.class, AgentTownLifeTerminalState::new);

    private String sessionId = "";
    private AgentTownLifeLifecycleEvent.Phase phase;
    private String reason = "";
    private long occurredAtMs;

    public synchronized void record(String nextSessionId,
                                    AgentTownLifeLifecycleEvent.Phase nextPhase,
                                    String nextReason,
                                    long nowMs) {
        if (nextPhase != AgentTownLifeLifecycleEvent.Phase.EXITED
                && nextPhase != AgentTownLifeLifecycleEvent.Phase.FORCED
                && nextPhase != AgentTownLifeLifecycleEvent.Phase.TIMED_OUT) {
            return;
        }
        sessionId = nextSessionId == null ? "" : nextSessionId.trim();
        phase = nextPhase;
        reason = nextReason == null ? "" : nextReason.trim();
        occurredAtMs = Math.max(0L, nowMs);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(sessionId, phase, reason, occurredAtMs);
    }

    public record Snapshot(String sessionId,
                           AgentTownLifeLifecycleEvent.Phase phase,
                           String reason,
                           long occurredAtMs) {
        public boolean matches(String expectedSessionId) {
            return expectedSessionId != null && !expectedSessionId.isBlank()
                    && expectedSessionId.equals(sessionId) && phase != null;
        }
    }
}
