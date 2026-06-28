package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed combat buff/support state.
 */
public final class AgentBotCombatBuffStateRuntime {
    private AgentBotCombatBuffStateRuntime() {
    }

    public static boolean skillBuffsEnabled(BotEntry entry) {
        return entry.skillBuffsEnabled();
    }

    public static void setSkillBuffsEnabled(BotEntry entry, boolean enabled) {
        entry.setSkillBuffsEnabled(enabled);
    }

    public static boolean supportHealsEnabled(BotEntry entry) {
        return entry.supportHealsEnabled();
    }

    public static void setSupportHealsEnabled(BotEntry entry, boolean enabled) {
        entry.setSupportHealsEnabled(enabled);
    }

    public static long nextBuffAt(BotEntry entry, int skillId) {
        return entry.nextBuffAt(skillId);
    }

    public static void ensureNextBuffAt(BotEntry entry, int skillId, long nextAt) {
        entry.ensureNextBuffAt(skillId, nextAt);
    }

    public static void setNextBuffAt(BotEntry entry, int skillId, long nextAt) {
        entry.setNextBuffAt(skillId, nextAt);
    }

    public static long nextSupportBuffAt(BotEntry entry, int skillId) {
        return entry.nextSupportBuffAt(skillId);
    }

    public static boolean supportBuffOnCooldown(BotEntry entry, int skillId, long nowMs) {
        return nowMs < nextSupportBuffAt(entry, skillId);
    }

    public static void setNextSupportBuffAt(BotEntry entry, int skillId, long nextAt) {
        entry.setNextSupportBuffAt(skillId, nextAt);
    }
}
