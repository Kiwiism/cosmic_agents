package server.agents.capabilities.combat;

import java.util.ArrayList;
import java.util.List;

/**
 * Cached attack/support skill choices for the current job, level, and skill signature.
 */
public final class AgentCombatSkillCacheState {
    private int jobId = -1;
    private int level = -1;
    private int signature;
    private final List<Integer> attackSkillIds = new ArrayList<>();
    private int attackSkillId;
    private int aoeSkillId;
    private int aoeSkillMobs = 1;
    private int healSkillId;
    private final List<Integer> buffSkillIds = new ArrayList<>();
    private final List<Integer> summonSkillIds = new ArrayList<>();

    public boolean matches(int jobId, int level, int signature) {
        return this.jobId == jobId && this.level == level && this.signature == signature;
    }

    public void reset(int jobId, int level, int signature) {
        this.jobId = jobId;
        this.level = level;
        this.signature = signature;
        attackSkillId = 0;
        aoeSkillId = 0;
        aoeSkillMobs = 1;
        attackSkillIds.clear();
        healSkillId = 0;
        buffSkillIds.clear();
        summonSkillIds.clear();
    }

    public List<Integer> attackSkillIds() {
        return attackSkillIds;
    }

    public void addAttackSkillId(int skillId) {
        attackSkillIds.add(skillId);
    }

    public int attackSkillId() {
        return attackSkillId;
    }

    public void setAttackSkillId(int attackSkillId) {
        this.attackSkillId = attackSkillId;
    }

    public int aoeSkillId() {
        return aoeSkillId;
    }

    public int aoeSkillMobs() {
        return aoeSkillMobs;
    }

    public void setAoeSkill(int skillId, int mobCount) {
        aoeSkillId = skillId;
        aoeSkillMobs = mobCount;
    }

    public int healSkillId() {
        return healSkillId;
    }

    public void setHealSkillId(int healSkillId) {
        this.healSkillId = healSkillId;
    }

    public List<Integer> buffSkillIds() {
        return buffSkillIds;
    }

    public void addBuffSkillId(int skillId) {
        buffSkillIds.add(skillId);
    }

    public List<Integer> summonSkillIds() {
        return summonSkillIds;
    }

    public void addSummonSkillId(int skillId) {
        summonSkillIds.add(skillId);
    }
}
