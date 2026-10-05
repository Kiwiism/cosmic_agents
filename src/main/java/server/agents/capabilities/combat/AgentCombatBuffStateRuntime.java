package server.agents.capabilities.combat;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned adapter for AgentRuntimeEntry-backed combat buff/support state.
 */
public final class AgentCombatBuffStateRuntime {
    private AgentCombatBuffStateRuntime() {
    }

    public static boolean skillBuffsEnabled(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentCombatBuffState.STATE_KEY).skillBuffsEnabled();
    }

    public static void setSkillBuffsEnabled(AgentRuntimeEntry entry, boolean enabled) {
        entry.capabilityStates().require(AgentCombatBuffState.STATE_KEY).setSkillBuffsEnabled(enabled);
    }

    public static boolean supportHealsEnabled(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentCombatBuffState.STATE_KEY).supportHealsEnabled();
    }

    public static void setSupportHealsEnabled(AgentRuntimeEntry entry, boolean enabled) {
        entry.capabilityStates().require(AgentCombatBuffState.STATE_KEY).setSupportHealsEnabled(enabled);
    }

    public static long nextBuffAt(AgentRuntimeEntry entry, int skillId) {
        return entry.capabilityStates().require(AgentCombatBuffState.STATE_KEY).nextBuffAt(skillId);
    }

    public static void ensureNextBuffAt(AgentRuntimeEntry entry, int skillId, long nextAt) {
        entry.capabilityStates().require(AgentCombatBuffState.STATE_KEY).ensureNextBuffAt(skillId, nextAt);
    }

    public static void setNextBuffAt(AgentRuntimeEntry entry, int skillId, long nextAt) {
        entry.capabilityStates().require(AgentCombatBuffState.STATE_KEY).setNextBuffAt(skillId, nextAt);
    }

    public static long nextSupportBuffAt(AgentRuntimeEntry entry, int skillId) {
        return entry.capabilityStates().require(AgentCombatBuffState.STATE_KEY).nextSupportBuffAt(skillId);
    }

    public static boolean supportBuffOnCooldown(AgentRuntimeEntry entry, int skillId, long nowMs) {
        return entry.capabilityStates().require(AgentCombatBuffState.STATE_KEY).supportBuffOnCooldown(skillId, nowMs);
    }

    public static void setNextSupportBuffAt(AgentRuntimeEntry entry, int skillId, long nextAt) {
        entry.capabilityStates().require(AgentCombatBuffState.STATE_KEY).setNextSupportBuffAt(skillId, nextAt);
    }

    /**
     * The owner asked for buffs ("buff me again"): make every buff skill due now, so the next buff
     * ticks recast them even while the old ones are still running. The server refreshes an active
     * buff on recast (StatEffect.applyBuffEffect cancels it first), so the party gets full timers.
     */
    public static void requestRebuff(AgentRuntimeEntry entry) {
        AgentCombatBuffState state = entry.capabilityStates().require(AgentCombatBuffState.STATE_KEY);
        for (int skillId : AgentCombatSkillCacheStateRuntime.buffSkillIds(entry)) {
            state.setNextBuffAt(skillId, 0L);
            state.setNextSupportBuffAt(skillId, 0L);
        }
    }
}
