package server.agents.integration;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.combat.AgentAttackPlan;
import server.agents.capabilities.combat.AgentAttackPlanScoringPolicy;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.combat.AgentCombatScoringPolicy;
import server.agents.capabilities.combat.AgentCombatTargetSelector;
import server.agents.capabilities.combat.AgentSkillAttackPlanRuntime;
import server.agents.capabilities.dialogue.AgentCombatDialogueReporter;
import server.bots.BotEntry;
import server.life.Monster;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;

public final class AgentBotCombatAoeRepositionRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentBotCombatAoeRepositionRuntime.class);

    private AgentBotCombatAoeRepositionRuntime() {
    }

    public static Point aoeRepositionTarget(BotEntry entry, Character bot, Monster primaryTarget,
                                            AgentAttackPlan fireNowBest, AgentCombatConfig.Config config) {
        boolean hasMultiMobAoeSkill = entry != null && AgentBotCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry);
        int aoeSkillId = entry == null ? 0 : AgentBotCombatSkillCacheStateRuntime.aoeSkillId(entry);
        int aoeSkillMobs = entry == null ? 0 : AgentBotCombatSkillCacheStateRuntime.aoeSkillMobs(entry);
        if (!AgentCombatScoringPolicy.shouldConsiderAoeReposition(
                config.AOE_REPOSITION_ENABLED,
                entry != null && bot != null,
                primaryTarget != null,
                hasMultiMobAoeSkill,
                fireNowBest != null,
                fireNowBest != null && fireNowBest.skillId == aoeSkillId,
                fireNowBest == null ? 0 : fireNowBest.targets.size(),
                aoeSkillMobs)) {
            return null;
        }
        Point botPos = bot.getPosition();
        Point tp = primaryTarget.getPosition();
        if (botPos == null || tp == null) {
            return null;
        }
        List<Monster> cluster = AgentCombatScoringPolicy.legacyClusterMonsters(
                primaryTarget, bot.getMap().getAllMonsters());
        if (cluster.size() <= fireNowBest.targets.size()) {
            return null;
        }
        int centroidX = AgentCombatScoringPolicy.clusterCentroidX(cluster);
        AgentAttackPlan aoeNow = AgentSkillAttackPlanRuntime.planSkillAttack(bot, primaryTarget, aoeSkillId, config);
        if (aoeNow == null || aoeNow.hitBox == null) {
            return null;
        }
        int shift = AgentCombatScoringPolicy.boundedRepositionShift(
                centroidX, aoeNow.hitBox.getCenterX(), config.AOE_REPOSITION_MAX_DISTANCE_X);
        if (AgentCombatScoringPolicy.isWithinRepositionArrival(shift, config.AOE_REPOSITION_ARRIVAL_X)) {
            return null;
        }
        Rectangle shifted = new Rectangle(aoeNow.hitBox);
        shifted.translate(shift, 0);
        Monster sweetPrimary = AgentCombatScoringPolicy.nearestMonster(cluster, centroidX, tp.y);
        if (sweetPrimary == null) {
            return null;
        }
        List<Monster> sweetTargets = AgentCombatTargetSelector.collectTargetsInHitBox(
                sweetPrimary, shifted, aoeSkillMobs, bot.getMap().getAllMonsters());
        if (sweetTargets.size() <= fireNowBest.targets.size()) {
            return null;
        }
        AgentAttackPlanScoringPolicy.AgentAttackPlanScore<AgentAttackPlan> fireNowScore =
                AgentAttackPlanScoringPolicy.scoreAttackPlan(bot, fireNowBest);
        if (fireNowScore.minimumKillsFullHpTargets()) {
            return null;
        }
        AgentAttackPlan sweetPlan = new AgentAttackPlan(aoeNow.skillId, aoeNow.skillLevel, aoeNow.numDamage, shifted,
                sweetTargets, aoeNow.route, aoeNow.display, aoeNow.direction, aoeNow.rangedDirection,
                aoeNow.stance, aoeNow.speed, aoeNow.hitDelayMs, aoeNow.cooldownMs, aoeNow.damageWeaponType);
        AgentAttackPlanScoringPolicy.AgentAttackPlanScore<AgentAttackPlan> sweetScore =
                AgentAttackPlanScoringPolicy.scoreAttackPlan(bot, sweetPlan);
        if (sweetScore.rawDps() >= fireNowScore.rawDps() * config.AOE_REPOSITION_DPS_FACTOR) {
            if (config.AOE_REPOSITION_DEBUG) {
                double pct = fireNowScore.rawDps() > 0
                        ? sweetScore.rawDps() / fireNowScore.rawDps() * 100.0d
                        : 0.0d;
                log.info("AoE reposition[{}]: stepping {}px {} to hit {} mobs (vs {}) with {} for {}% DPS ({} vs {} dps)",
                        bot.getName(), Math.abs(shift), shift < 0 ? "left" : "right",
                        sweetTargets.size(), fireNowBest.targets.size(),
                        AgentCombatDialogueReporter.combatSkillLabel(aoeSkillId),
                        Math.round(pct), Math.round(sweetScore.rawDps()), Math.round(fireNowScore.rawDps()));
            }
            return new Point(botPos.x + shift, botPos.y);
        }
        return null;
    }
}
