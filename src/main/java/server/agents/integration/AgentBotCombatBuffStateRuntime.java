package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed combat buff/support state.
 */
public final class AgentBotCombatBuffStateRuntime {
    private AgentBotCombatBuffStateRuntime() {
    }

    public static boolean skillBuffsEnabled(BotEntry entry) {
        return entry.combatBuffState().skillBuffsEnabled();
    }

    public static void setSkillBuffsEnabled(BotEntry entry, boolean enabled) {
        entry.combatBuffState().setSkillBuffsEnabled(enabled);
    }

    public static boolean supportHealsEnabled(BotEntry entry) {
        return entry.combatBuffState().supportHealsEnabled();
    }

    public static void setSupportHealsEnabled(BotEntry entry, boolean enabled) {
        entry.combatBuffState().setSupportHealsEnabled(enabled);
    }

    public static long nextBuffAt(BotEntry entry, int skillId) {
        return entry.combatBuffState().nextBuffAt(skillId);
    }

    public static void ensureNextBuffAt(BotEntry entry, int skillId, long nextAt) {
        entry.combatBuffState().ensureNextBuffAt(skillId, nextAt);
    }

    public static void setNextBuffAt(BotEntry entry, int skillId, long nextAt) {
        entry.combatBuffState().setNextBuffAt(skillId, nextAt);
    }

    public static long nextSupportBuffAt(BotEntry entry, int skillId) {
        return entry.combatBuffState().nextSupportBuffAt(skillId);
    }

    public static boolean supportBuffOnCooldown(BotEntry entry, int skillId, long nowMs) {
        return entry.combatBuffState().supportBuffOnCooldown(skillId, nowMs);
    }

    public static void setNextSupportBuffAt(BotEntry entry, int skillId, long nextAt) {
        entry.combatBuffState().setNextSupportBuffAt(skillId, nextAt);
    }
}
