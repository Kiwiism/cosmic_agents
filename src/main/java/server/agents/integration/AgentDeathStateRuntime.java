package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned adapter for AgentRuntimeEntry-backed death/respawn window state.
 */
public final class AgentDeathStateRuntime {
    private AgentDeathStateRuntime() {
    }

    public static long deadUntilMs(AgentRuntimeEntry entry) {
        return entry.deathState().deadUntilMs();
    }

    public static boolean isDead(AgentRuntimeEntry entry) {
        return entry.deathState().isDead();
    }

    public static boolean shouldEnterDeadState(AgentRuntimeEntry entry, int hp) {
        return entry.deathState().shouldEnterDeadState(hp);
    }

    public static boolean isRespawnDue(AgentRuntimeEntry entry, long nowMs) {
        return entry.deathState().isRespawnDue(nowMs);
    }

    public static void enterDeadState(AgentRuntimeEntry entry, long nowMs, long deadDurationMs) {
        entry.deathState().enterDeadState(nowMs, deadDurationMs);
    }

    public static void clear(AgentRuntimeEntry entry) {
        entry.deathState().clear();
    }
}
