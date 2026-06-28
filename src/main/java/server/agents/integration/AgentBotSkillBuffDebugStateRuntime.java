package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed skill-buff debug state.
 */
public final class AgentBotSkillBuffDebugStateRuntime {
    private AgentBotSkillBuffDebugStateRuntime() {
    }

    public static long lastActionAtMs(BotEntry entry) {
        return entry.lastSkillBuffActionAtMs();
    }

    public static String lastActionSummary(BotEntry entry) {
        return entry.lastSkillBuffActionSummary();
    }

    public static long lastActionAgeMs(BotEntry entry, long nowMs) {
        long lastActionAtMs = lastActionAtMs(entry);
        return lastActionAtMs > 0 ? Math.max(0L, nowMs - lastActionAtMs) : -1L;
    }

    public static void rememberAction(BotEntry entry, long atMs, String summary) {
        entry.rememberSkillBuffAction(atMs, summary);
    }
}
