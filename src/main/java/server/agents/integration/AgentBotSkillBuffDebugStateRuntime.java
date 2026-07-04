package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed skill-buff debug state.
 */
public final class AgentBotSkillBuffDebugStateRuntime {
    private AgentBotSkillBuffDebugStateRuntime() {
    }

    public static long lastActionAtMs(BotEntry entry) {
        return entry.buffState().lastSkillActionAtMs();
    }

    public static String lastActionSummary(BotEntry entry) {
        return entry.buffState().lastSkillActionSummary();
    }

    public static long lastActionAgeMs(BotEntry entry, long nowMs) {
        return entry.buffState().lastSkillActionAgeMs(nowMs);
    }

    public static void rememberAction(BotEntry entry, long atMs, String summary) {
        entry.buffState().rememberSkillAction(atMs, summary);
    }
}
