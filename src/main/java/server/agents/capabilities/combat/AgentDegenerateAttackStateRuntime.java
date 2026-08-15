package server.agents.capabilities.combat;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned adapter for temporary AgentRuntimeEntry-backed ranged degenerate-hit state.
 */
public final class AgentDegenerateAttackStateRuntime {
    private AgentDegenerateAttackStateRuntime() {
    }

    public static boolean degenAttackDone(AgentRuntimeEntry entry) {
        return AgentRangedTacticalStateRuntime.state(entry).degenerateAttack().done();
    }

    public static void markDegenAttackDone(AgentRuntimeEntry entry) {
        AgentRangedTacticalStateRuntime.state(entry).degenerateAttack().markDone();
    }

    public static void clear(AgentRuntimeEntry entry) {
        AgentRangedTacticalStateRuntime.state(entry).degenerateAttack().clear();
    }
}
