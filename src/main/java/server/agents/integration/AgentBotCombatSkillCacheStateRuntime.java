package server.agents.integration;

import server.bots.BotEntry;

import java.util.List;

/**
 * Agent-owned adapter for temporary BotEntry-backed combat skill cache state.
 */
public final class AgentBotCombatSkillCacheStateRuntime {
    private AgentBotCombatSkillCacheStateRuntime() {
    }

    public static boolean matches(BotEntry entry, int jobId, int level, int signature) {
        return entry.skillCacheMatches(jobId, level, signature);
    }

    public static void reset(BotEntry entry, int jobId, int level, int signature) {
        entry.resetSkillCache(jobId, level, signature);
    }

    public static List<Integer> attackSkillIds(BotEntry entry) {
        return entry.attackSkillIds();
    }

    public static boolean hasAttackSkillIds(BotEntry entry) {
        return !attackSkillIds(entry).isEmpty();
    }

    public static void addAttackSkillId(BotEntry entry, int skillId) {
        entry.addAttackSkillId(skillId);
    }

    public static int attackSkillId(BotEntry entry) {
        return entry.attackSkillId();
    }

    public static boolean hasAttackSkill(BotEntry entry) {
        return attackSkillId(entry) != 0;
    }

    public static void setAttackSkillId(BotEntry entry, int skillId) {
        entry.setAttackSkillId(skillId);
    }

    public static int aoeSkillId(BotEntry entry) {
        return entry.aoeSkillId();
    }

    public static int aoeSkillMobs(BotEntry entry) {
        return entry.aoeSkillMobs();
    }

    public static boolean hasAoeSkill(BotEntry entry) {
        return aoeSkillId(entry) != 0;
    }

    public static boolean hasMultiMobAoeSkill(BotEntry entry) {
        return aoeSkillId(entry) != 0 && aoeSkillMobs(entry) > 1;
    }

    public static void setAoeSkill(BotEntry entry, int skillId, int mobCount) {
        entry.setAoeSkill(skillId, mobCount);
    }

    public static int healSkillId(BotEntry entry) {
        return entry.healSkillId();
    }

    public static boolean hasHealSkill(BotEntry entry) {
        return healSkillId(entry) != 0;
    }

    public static void setHealSkillId(BotEntry entry, int skillId) {
        entry.setHealSkillId(skillId);
    }

    public static List<Integer> buffSkillIds(BotEntry entry) {
        return entry.buffSkillIds();
    }

    public static boolean hasBuffSkillIds(BotEntry entry) {
        return !buffSkillIds(entry).isEmpty();
    }

    public static void addBuffSkillId(BotEntry entry, int skillId) {
        entry.addBuffSkillId(skillId);
    }

    public static List<Integer> summonSkillIds(BotEntry entry) {
        return entry.summonSkillIds();
    }

    public static void addSummonSkillId(BotEntry entry, int skillId) {
        entry.addSummonSkillId(skillId);
    }
}
