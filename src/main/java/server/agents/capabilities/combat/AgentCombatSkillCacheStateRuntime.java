package server.agents.capabilities.combat;

import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

/**
 * Agent-owned adapter for AgentRuntimeEntry-backed combat skill cache state.
 */
public final class AgentCombatSkillCacheStateRuntime {
    private AgentCombatSkillCacheStateRuntime() {
    }

    public static boolean matches(AgentRuntimeEntry entry, int jobId, int level, int signature) {
        return entry.combatSkillCacheState().matches(jobId, level, signature);
    }

    public static void reset(AgentRuntimeEntry entry, int jobId, int level, int signature) {
        entry.combatSkillCacheState().reset(jobId, level, signature);
    }

    public static List<Integer> attackSkillIds(AgentRuntimeEntry entry) {
        return entry.combatSkillCacheState().attackSkillIds();
    }

    public static boolean hasAttackSkillIds(AgentRuntimeEntry entry) {
        return !attackSkillIds(entry).isEmpty();
    }

    public static void addAttackSkillId(AgentRuntimeEntry entry, int skillId) {
        entry.combatSkillCacheState().addAttackSkillId(skillId);
    }

    public static int attackSkillId(AgentRuntimeEntry entry) {
        return entry.combatSkillCacheState().attackSkillId();
    }

    public static boolean hasAttackSkill(AgentRuntimeEntry entry) {
        return attackSkillId(entry) != 0;
    }

    public static void setAttackSkillId(AgentRuntimeEntry entry, int skillId) {
        entry.combatSkillCacheState().setAttackSkillId(skillId);
    }

    public static int aoeSkillId(AgentRuntimeEntry entry) {
        return entry.combatSkillCacheState().aoeSkillId();
    }

    public static int aoeSkillMobs(AgentRuntimeEntry entry) {
        return entry.combatSkillCacheState().aoeSkillMobs();
    }

    public static boolean hasAoeSkill(AgentRuntimeEntry entry) {
        return aoeSkillId(entry) != 0;
    }

    public static boolean hasMultiMobAoeSkill(AgentRuntimeEntry entry) {
        return aoeSkillId(entry) != 0 && aoeSkillMobs(entry) > 1;
    }

    public static void setAoeSkill(AgentRuntimeEntry entry, int skillId, int mobCount) {
        entry.combatSkillCacheState().setAoeSkill(skillId, mobCount);
    }

    public static int healSkillId(AgentRuntimeEntry entry) {
        return entry.combatSkillCacheState().healSkillId();
    }

    public static boolean hasHealSkill(AgentRuntimeEntry entry) {
        return healSkillId(entry) != 0;
    }

    public static void setHealSkillId(AgentRuntimeEntry entry, int skillId) {
        entry.combatSkillCacheState().setHealSkillId(skillId);
    }

    public static List<Integer> buffSkillIds(AgentRuntimeEntry entry) {
        return entry.combatSkillCacheState().buffSkillIds();
    }

    public static boolean hasBuffSkillIds(AgentRuntimeEntry entry) {
        return !buffSkillIds(entry).isEmpty();
    }

    public static void addBuffSkillId(AgentRuntimeEntry entry, int skillId) {
        entry.combatSkillCacheState().addBuffSkillId(skillId);
    }

    public static List<Integer> summonSkillIds(AgentRuntimeEntry entry) {
        return entry.combatSkillCacheState().summonSkillIds();
    }

    public static void addSummonSkillId(AgentRuntimeEntry entry, int skillId) {
        entry.combatSkillCacheState().addSummonSkillId(skillId);
    }
}
