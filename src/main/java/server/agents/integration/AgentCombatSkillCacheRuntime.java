package server.agents.integration;

import client.Character;
import client.Skill;
import server.StatEffect;
import server.agents.capabilities.combat.AgentCombatHitCounter;
import server.agents.capabilities.combat.AgentCombatSkillClassifier;
import server.agents.runtime.AgentRuntimeEntry;

public final class AgentCombatSkillCacheRuntime {
    private AgentCombatSkillCacheRuntime() {
    }

    public static void rebuildSkillCacheIfNeeded(AgentRuntimeEntry entry, Character bot) {
        int skillSignature = AgentCombatSkillClassifier.skillCacheSignature(bot);
        if (AgentCombatSkillCacheStateRuntime.matches(
                entry, bot.getJob().getId(), bot.getLevel(), skillSignature)) {
            return;
        }

        AgentCombatSkillCacheStateRuntime.reset(entry, bot.getJob().getId(), bot.getLevel(), skillSignature);

        int bestAtkHits = 0;
        int bestAtkPriority = Integer.MIN_VALUE;
        int bestAtkDamage = Integer.MIN_VALUE;
        long bestAoeScore = 0;

        for (Skill skill : bot.getSkills().keySet()) {
            int lvl = bot.getSkillLevel(skill);
            if (lvl <= 0) continue;

            StatEffect fx = skill.getEffect(lvl);
            int atk = AgentCombatHitCounter.effectiveHitCount(fx);
            int mobs = fx.getMobCount();
            AgentCombatSkillClassifier.SkillCacheBucket cacheBucket =
                    AgentCombatSkillClassifier.classifySkillCacheBucket(skill, fx);

            if (AgentCombatSkillClassifier.shouldStopCacheScanAfterHealSkill(skill)) {
                if (cacheBucket == AgentCombatSkillClassifier.SkillCacheBucket.ACTIVE_HEAL) {
                    AgentCombatSkillCacheStateRuntime.setHealSkillId(entry, skill.getId());
                }
                continue;  // not an attack skill; offensive use against undead handled in tickSupportHealing
            }

            if (cacheBucket == AgentCombatSkillClassifier.SkillCacheBucket.ACTIVE_ATTACK) {
                AgentCombatSkillCacheStateRuntime.addAttackSkillId(entry, skill.getId());
                if (mobs >= 2) {
                    long score = AgentCombatSkillClassifier.aoeSkillScore(fx, atk, mobs);
                    if (score > bestAoeScore) {
                        bestAoeScore = score;
                        AgentCombatSkillCacheStateRuntime.setAoeSkill(entry, skill.getId(), mobs);
                    }
                } else if (AgentCombatSkillClassifier.shouldUseAsBestSingleTargetSkill(bot, skill, fx, atk,
                        bestAtkHits, bestAtkPriority, bestAtkDamage,
                        AgentCombatSkillCacheStateRuntime.attackSkillId(entry))) {
                    bestAtkHits = atk;
                    bestAtkPriority = AgentCombatSkillClassifier.singleTargetSkillPriority(bot, skill);
                    bestAtkDamage = fx.getDamage();
                    AgentCombatSkillCacheStateRuntime.setAttackSkillId(entry, skill.getId());
                }
                continue;
            }

            if (cacheBucket == AgentCombatSkillClassifier.SkillCacheBucket.SUMMON) {
                // Own bucket, not rebuffable - see AgentCombatSkillCacheState.summonSkillIds.
                AgentCombatSkillCacheStateRuntime.addSummonSkillId(entry, skill.getId());
                continue;
            }

            if (cacheBucket != AgentCombatSkillClassifier.SkillCacheBucket.SUPPORT_BUFF) continue;
            AgentCombatSkillCacheStateRuntime.addBuffSkillId(entry, skill.getId());
            AgentCombatBuffStateRuntime.ensureNextBuffAt(entry, skill.getId(), 0L);
        }
    }
}
