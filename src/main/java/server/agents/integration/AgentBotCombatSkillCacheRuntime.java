package server.agents.integration;

import client.Character;
import client.Skill;
import server.StatEffect;
import server.agents.capabilities.combat.AgentCombatHitCounter;
import server.agents.capabilities.combat.AgentCombatSkillClassifier;
import server.bots.BotEntry;

public final class AgentBotCombatSkillCacheRuntime {
    private AgentBotCombatSkillCacheRuntime() {
    }

    public static void rebuildSkillCacheIfNeeded(BotEntry entry, Character bot) {
        int skillSignature = AgentCombatSkillClassifier.skillCacheSignature(bot);
        if (AgentBotCombatSkillCacheStateRuntime.matches(
                entry, bot.getJob().getId(), bot.getLevel(), skillSignature)) {
            return;
        }

        AgentBotCombatSkillCacheStateRuntime.reset(entry, bot.getJob().getId(), bot.getLevel(), skillSignature);

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
                    AgentBotCombatSkillCacheStateRuntime.setHealSkillId(entry, skill.getId());
                }
                continue;  // not an attack skill; offensive use against undead handled in tickSupportHealing
            }

            if (cacheBucket == AgentCombatSkillClassifier.SkillCacheBucket.ACTIVE_ATTACK) {
                AgentBotCombatSkillCacheStateRuntime.addAttackSkillId(entry, skill.getId());
                if (mobs >= 2) {
                    long score = AgentCombatSkillClassifier.aoeSkillScore(fx, atk, mobs);
                    if (score > bestAoeScore) {
                        bestAoeScore = score;
                        AgentBotCombatSkillCacheStateRuntime.setAoeSkill(entry, skill.getId(), mobs);
                    }
                } else if (AgentCombatSkillClassifier.shouldUseAsBestSingleTargetSkill(bot, skill, fx, atk,
                        bestAtkHits, bestAtkPriority, bestAtkDamage,
                        AgentBotCombatSkillCacheStateRuntime.attackSkillId(entry))) {
                    bestAtkHits = atk;
                    bestAtkPriority = AgentCombatSkillClassifier.singleTargetSkillPriority(bot, skill);
                    bestAtkDamage = fx.getDamage();
                    AgentBotCombatSkillCacheStateRuntime.setAttackSkillId(entry, skill.getId());
                }
                continue;
            }

            if (cacheBucket == AgentCombatSkillClassifier.SkillCacheBucket.SUMMON) {
                // Own bucket, not rebuffable - see BotEntry.summonSkillIds.
                AgentBotCombatSkillCacheStateRuntime.addSummonSkillId(entry, skill.getId());
                continue;
            }

            if (cacheBucket != AgentCombatSkillClassifier.SkillCacheBucket.SUPPORT_BUFF) continue;
            AgentBotCombatSkillCacheStateRuntime.addBuffSkillId(entry, skill.getId());
            AgentBotCombatBuffStateRuntime.ensureNextBuffAt(entry, skill.getId(), 0L);
        }
    }
}
