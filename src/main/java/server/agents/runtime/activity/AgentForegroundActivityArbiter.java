package server.agents.runtime.activity;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

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
                state.select(activity.id(), nowMs);
                return outcome.consumedTick();
            }
        }
        state.clear(nowMs);
        return false;
    }
}
