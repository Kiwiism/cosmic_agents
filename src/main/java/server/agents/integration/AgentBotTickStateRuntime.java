package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed tick and heartbeat
 * metadata.
 */
public final class AgentBotTickStateRuntime {
    private AgentBotTickStateRuntime() {
    }

    public static boolean lastTickWasAi(BotEntry entry) {
        return entry.tickState().lastTickWasAi();
    }

    public static long lastTickAtMs(BotEntry entry) {
        return entry.tickState().lastTickAtMs();
    }

    public static void recordTick(BotEntry entry, boolean aiTick, long tickAtMs) {
        entry.tickState().recordTick(aiTick, tickAtMs);
    }

    public static boolean heartbeatDue(BotEntry entry, long nowMs, long intervalMs) {
        return entry.tickState().heartbeatDue(nowMs, intervalMs);
    }

    public static void markHeartbeat(BotEntry entry, long nowMs) {
        entry.tickState().setLastHeartbeatAtMs(nowMs);
    }

    public static long nextFollowIdleMovementCheckAtMs(BotEntry entry) {
        return entry.tickState().nextFollowIdleMovementCheckAtMs();
    }

    public static void setNextFollowIdleMovementCheckAtMs(BotEntry entry, long nextCheckAtMs) {
        entry.tickState().setNextFollowIdleMovementCheckAtMs(nextCheckAtMs);
    }
}
