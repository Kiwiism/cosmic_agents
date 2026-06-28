package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed death/respawn window state.
 */
public final class AgentBotDeathStateRuntime {
    private AgentBotDeathStateRuntime() {
    }

    public static long deadUntilMs(BotEntry entry) {
        return entry.deadUntilMs();
    }

    public static boolean isDead(BotEntry entry) {
        return deadUntilMs(entry) > 0L;
    }

    public static boolean shouldEnterDeadState(BotEntry entry, int hp) {
        return !isDead(entry) && hp <= 0;
    }

    public static boolean isRespawnDue(BotEntry entry, long nowMs) {
        long deadUntilMs = deadUntilMs(entry);
        return deadUntilMs > 0L && nowMs >= deadUntilMs;
    }

    public static void enterDeadState(BotEntry entry, long nowMs, long deadDurationMs) {
        entry.setDeadUntilMs(nowMs + deadDurationMs);
    }

    public static void clear(BotEntry entry) {
        entry.clearDeadUntilMs();
    }
}
