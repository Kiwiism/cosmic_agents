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
        return entry.lastTickWasAi();
    }

    public static long lastTickAtMs(BotEntry entry) {
        return entry.lastTickAtMs();
    }

    public static void recordTick(BotEntry entry, boolean aiTick, long tickAtMs) {
        entry.recordTick(aiTick, tickAtMs);
    }

    public static boolean heartbeatDue(BotEntry entry, long nowMs, long intervalMs) {
        return nowMs - entry.lastHeartbeatAtMs() >= intervalMs;
    }

    public static void markHeartbeat(BotEntry entry, long nowMs) {
        entry.setLastHeartbeatAtMs(nowMs);
    }

    public static long nextFollowIdleMovementCheckAtMs(BotEntry entry) {
        return entry.nextFollowIdleMovementCheckAtMs();
    }

    public static void setNextFollowIdleMovementCheckAtMs(BotEntry entry, long nextCheckAtMs) {
        entry.setNextFollowIdleMovementCheckAtMs(nextCheckAtMs);
    }
}
