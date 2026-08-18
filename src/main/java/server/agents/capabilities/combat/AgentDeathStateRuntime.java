package server.agents.capabilities.combat;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned adapter for AgentRuntimeEntry-backed death/respawn window state.
 */
public final class AgentDeathStateRuntime {
    private AgentDeathStateRuntime() {
    }

    public static long deadUntilMs(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentDeathState.STATE_KEY).deadUntilMs();
    }

    public static boolean isDead(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentDeathState.STATE_KEY).isDead();
    }

    public static boolean shouldEnterDeadState(AgentRuntimeEntry entry, int hp) {
        return entry.capabilityStates().require(AgentDeathState.STATE_KEY).shouldEnterDeadState(hp);
    }

    public static boolean isRespawnDue(AgentRuntimeEntry entry, long nowMs) {
        return entry.capabilityStates().require(AgentDeathState.STATE_KEY).isRespawnDue(nowMs);
    }

    public static void enterDeadState(AgentRuntimeEntry entry, long nowMs, long deadDurationMs) {
        entry.capabilityStates().require(AgentDeathState.STATE_KEY).enterDeadState(nowMs, deadDurationMs);
    }

    public static void clear(AgentRuntimeEntry entry) {
        entry.capabilityStates().require(AgentDeathState.STATE_KEY).clear();
    }
}
