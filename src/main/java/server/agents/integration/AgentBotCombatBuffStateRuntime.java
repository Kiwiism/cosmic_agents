package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed combat buff/support state.
 */
public final class AgentBotCombatBuffStateRuntime {
    private AgentBotCombatBuffStateRuntime() {
    }

    public static boolean skillBuffsEnabled(AgentRuntimeEntry entry) {
        return entry.combatBuffState().skillBuffsEnabled();
    }

    public static void setSkillBuffsEnabled(AgentRuntimeEntry entry, boolean enabled) {
        entry.combatBuffState().setSkillBuffsEnabled(enabled);
    }

    public static boolean supportHealsEnabled(AgentRuntimeEntry entry) {
        return entry.combatBuffState().supportHealsEnabled();
    }

    public static void setSupportHealsEnabled(AgentRuntimeEntry entry, boolean enabled) {
        entry.combatBuffState().setSupportHealsEnabled(enabled);
    }

    public static long nextBuffAt(AgentRuntimeEntry entry, int skillId) {
        return entry.combatBuffState().nextBuffAt(skillId);
    }

    public static void ensureNextBuffAt(AgentRuntimeEntry entry, int skillId, long nextAt) {
        entry.combatBuffState().ensureNextBuffAt(skillId, nextAt);
    }

    public static void setNextBuffAt(AgentRuntimeEntry entry, int skillId, long nextAt) {
        entry.combatBuffState().setNextBuffAt(skillId, nextAt);
    }

    public static long nextSupportBuffAt(AgentRuntimeEntry entry, int skillId) {
        return entry.combatBuffState().nextSupportBuffAt(skillId);
    }

    public static boolean supportBuffOnCooldown(AgentRuntimeEntry entry, int skillId, long nowMs) {
        return entry.combatBuffState().supportBuffOnCooldown(skillId, nowMs);
    }

    public static void setNextSupportBuffAt(AgentRuntimeEntry entry, int skillId, long nextAt) {
        entry.combatBuffState().setNextSupportBuffAt(skillId, nextAt);
    }
}
