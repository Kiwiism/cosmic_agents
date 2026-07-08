package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned adapter for AgentRuntimeEntry-backed tick and heartbeat
 * metadata.
 */
public final class AgentTickStateRuntime {
    private AgentTickStateRuntime() {
    }

    public static boolean lastTickWasAi(AgentRuntimeEntry entry) {
        return entry.tickState().lastTickWasAi();
    }

    public static long lastTickAtMs(AgentRuntimeEntry entry) {
        return entry.tickState().lastTickAtMs();
    }

    public static void recordTick(AgentRuntimeEntry entry, boolean aiTick, long tickAtMs) {
        entry.tickState().recordTick(aiTick, tickAtMs);
    }

    public static boolean heartbeatDue(AgentRuntimeEntry entry, long nowMs, long intervalMs) {
        return entry.tickState().heartbeatDue(nowMs, intervalMs);
    }

    public static void markHeartbeat(AgentRuntimeEntry entry, long nowMs) {
        entry.tickState().setLastHeartbeatAtMs(nowMs);
    }

    public static long nextFollowIdleMovementCheckAtMs(AgentRuntimeEntry entry) {
        return entry.tickState().nextFollowIdleMovementCheckAtMs();
    }

    public static void setNextFollowIdleMovementCheckAtMs(AgentRuntimeEntry entry, long nextCheckAtMs) {
        entry.tickState().setNextFollowIdleMovementCheckAtMs(nextCheckAtMs);
    }
}
