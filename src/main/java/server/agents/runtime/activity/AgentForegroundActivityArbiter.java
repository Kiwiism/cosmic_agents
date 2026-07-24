package server.agents.runtime.activity;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.decision.AgentDecisionProvenanceState;

import java.util.List;

/**
 * Selects one foreground owner without coupling the scheduler to concrete
 * plans, TownLife, handoffs, or capability implementations.
 */
public final class AgentForegroundActivityArbiter {
    private final AgentForegroundActivityRegistry registry;

    public AgentForegroundActivityArbiter(AgentForegroundActivityRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("Foreground activity registry is required");
        }
        this.registry = registry;
    }

    public boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null) {
            return false;
        }
        AgentForegroundActivityState state =
                entry.capabilityStates().require(AgentForegroundActivityState.STATE_KEY);
        for (AgentForegroundActivity activity : registry.activities()) {
            if (!activity.active(entry, agent)) {
                continue;
            }
            AgentForegroundActivityTick outcome = activity.tick(entry, agent, nowMs);
            if (outcome == null) {
                throw new IllegalStateException(
                        "Foreground activity returned no outcome: " + activity.id());
            }
            if (outcome.ownsForeground()) {
                if (state.select(activity.id(), nowMs)) {
                    entry.capabilityStates().require(AgentDecisionProvenanceState.STATE_KEY).record(
                            nowMs,
                            "foreground-activity",
                            activity.id(),
                            "foreground-arbiter",
                            "foreground-arbiter-v1",
                            "highest-priority active owner",
                            "",
                            registry.activities().stream()
                                    .map(AgentForegroundActivity::id).toList());
                }
                return outcome.consumedTick();
            }
        }
        if (state.clear(nowMs)) {
            entry.capabilityStates().require(AgentDecisionProvenanceState.STATE_KEY).record(
                    nowMs, "foreground-activity", "none", "foreground-arbiter",
                    "foreground-arbiter-v1", "no foreground activity remained active",
                    "", List.of());
        }
        return false;
    }
}
