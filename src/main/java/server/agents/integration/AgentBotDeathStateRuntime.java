package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed death/respawn window state.
 */
public final class AgentBotDeathStateRuntime {
    private AgentBotDeathStateRuntime() {
    }

    public static long deadUntilMs(BotEntry entry) {
        return entry.deathState().deadUntilMs();
    }

    public static boolean isDead(BotEntry entry) {
        return entry.deathState().isDead();
    }

    public static boolean shouldEnterDeadState(BotEntry entry, int hp) {
        return entry.deathState().shouldEnterDeadState(hp);
    }

    public static boolean isRespawnDue(BotEntry entry, long nowMs) {
        return entry.deathState().isRespawnDue(nowMs);
    }

    public static void enterDeadState(BotEntry entry, long nowMs, long deadDurationMs) {
        entry.deathState().enterDeadState(nowMs, deadDurationMs);
    }

    public static void clear(BotEntry entry) {
        entry.deathState().clear();
    }
}
