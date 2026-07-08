package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned adapter for temporary AgentRuntimeEntry-backed grind retarget search cadence.
 */
public final class AgentGrindSearchStateRuntime {
    private AgentGrindSearchStateRuntime() {
    }

    public static long nextSearchAtMs(AgentRuntimeEntry entry) {
        return entry.grindTargetState().nextSearchAtMs();
    }

    public static boolean searchBlocked(AgentRuntimeEntry entry, long nowMs) {
        return nowMs < nextSearchAtMs(entry);
    }

    public static void scheduleNextSearch(AgentRuntimeEntry entry, long nextSearchAtMs) {
        entry.grindTargetState().setNextSearchAtMs(nextSearchAtMs);
    }

    public static void clear(AgentRuntimeEntry entry) {
        entry.grindTargetState().clearNextSearchAtMs();
    }
}
