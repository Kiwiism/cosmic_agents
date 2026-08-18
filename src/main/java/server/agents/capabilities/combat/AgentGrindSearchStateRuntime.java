package server.agents.capabilities.combat;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Capability-owned adapter for grind retarget search cadence.
 */
public final class AgentGrindSearchStateRuntime {
    private AgentGrindSearchStateRuntime() {
    }

    public static long nextSearchAtMs(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentGrindTargetState.STATE_KEY).nextSearchAtMs();
    }

    public static boolean searchBlocked(AgentRuntimeEntry entry, long nowMs) {
        return nowMs < nextSearchAtMs(entry);
    }

    public static void scheduleNextSearch(AgentRuntimeEntry entry, long nextSearchAtMs) {
        entry.capabilityStates().require(AgentGrindTargetState.STATE_KEY).setNextSearchAtMs(nextSearchAtMs);
    }

    public static void clear(AgentRuntimeEntry entry) {
        entry.capabilityStates().require(AgentGrindTargetState.STATE_KEY).clearNextSearchAtMs();
    }
}
