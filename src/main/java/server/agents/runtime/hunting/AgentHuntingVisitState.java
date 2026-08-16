package server.agents.runtime.hunting;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Last typed child visit accepted by Hunting; used for ownership diagnostics. */
public final class AgentHuntingVisitState {
    public static final AgentCapabilityStateKey<AgentHuntingVisitState> STATE_KEY =
            new AgentCapabilityStateKey<>("runtime.hunting-visit",
                    AgentHuntingVisitState.class, AgentHuntingVisitState::new);

    private AgentHuntingVisitRequest request;
    private long updatedAtMs;

    synchronized void record(AgentHuntingVisitRequest next, long nowMs) {
        request = next;
        updatedAtMs = Math.max(0L, nowMs);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(request, updatedAtMs);
    }

    public record Snapshot(AgentHuntingVisitRequest request, long updatedAtMs) {
    }
}
