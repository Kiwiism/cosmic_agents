package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned adapter for AgentRuntimeEntry-backed tick failure window state.
 */
public final class AgentTickFailureStateRuntime {
    private AgentTickFailureStateRuntime() {
    }

    public static int failureCount(AgentRuntimeEntry entry) {
        return entry.tickFailureState().failureCount();
    }

    public static long windowStartedAtMs(AgentRuntimeEntry entry) {
        return entry.tickFailureState().windowStartedAtMs();
    }

    public static int recordFailure(AgentRuntimeEntry entry, long nowMs, long windowMs) {
        if (nowMs - windowStartedAtMs(entry) > windowMs) {
            entry.tickFailureState().resetWindow(nowMs);
        }
        return entry.tickFailureState().incrementFailureCount();
    }

    public static boolean hasFailures(AgentRuntimeEntry entry) {
        return failureCount(entry) > 0;
    }

    public static void clear(AgentRuntimeEntry entry) {
        entry.tickFailureState().clear();
    }
}
