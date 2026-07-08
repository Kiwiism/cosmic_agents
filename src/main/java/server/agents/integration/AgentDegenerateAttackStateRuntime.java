package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned adapter for temporary AgentRuntimeEntry-backed ranged degenerate-hit state.
 */
public final class AgentDegenerateAttackStateRuntime {
    private AgentDegenerateAttackStateRuntime() {
    }

    public static boolean degenAttackDone(AgentRuntimeEntry entry) {
        return entry.degenerateAttackState().done();
    }

    public static void markDegenAttackDone(AgentRuntimeEntry entry) {
        entry.degenerateAttackState().markDone();
    }

    public static void clear(AgentRuntimeEntry entry) {
        entry.degenerateAttackState().clear();
    }
}
