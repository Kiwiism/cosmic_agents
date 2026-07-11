package server.agents.capabilities.combat;

import client.Character;
import client.BuffStat;
import client.Skill;
import client.inventory.WeaponType;
import constants.skills.DragonKnight;
import server.StatEffect;
import server.agents.capabilities.combat.data.AgentAttackDataProvider;
import server.agents.integration.AgentSkillGatewayRuntime;
import server.agents.integration.SkillGateway;
import server.life.Monster;

import java.awt.Rectangle;
import java.util.List;

public final class AgentSkillAttackPlanRuntime {
    private AgentSkillAttackPlanRuntime() {
    }

    public static AgentAttackPlan planSkillAttack(Character bot, Monster primaryTarget, int skillId,
                                                  AgentCombatConfig.Config config) {
        return planSkillAttack(bot, primaryTarget, skillId, config, AgentSkillGatewayRuntime.skills());
    }

    public static AgentAttackPlan planSkillAttack(Character bot, Monster primaryTarget, int skillId,
                                                  AgentCombatConfig.Config config, SkillGateway skills) {
        Skill skill = skills.getSkill(skillId);
        int skillLevel = skill == null ? 0 : bot.getSkillLevel(skill);
        StatEffect effect = skill == null || skillLevel <= 0 ? null : skill.getEffect(skillLevel);
        if (!AgentComboFinisherPolicy.canPlan(skillId, bot.getBuffedValue(BuffStat.COMBO))) {
            return null;
        }
        AgentSkillAttackPlanner.SkillAttackReadiness readiness = AgentSkillAttackPlanner.skillAttackReadiness(
                skillId,
                skillId != 0 && bot.skillIsCooling(skillId),
                skill != null,
                skillLevel,
                () -> effect.canPaySkillCost(bot),
                () -> AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                        skillId, AgentAttackExecutionProvider.getEquippedWeaponType(bot)));
        if (readiness != AgentSkillAttackPlanner.SkillAttackReadiness.READY) {
            return null;
        }

        WeaponType weaponType = AgentAttackExecutionProvider.getEquippedWeaponType(bot);
        AgentAttackRoute route = AgentAttackExecutionProvider.determineSkillRoute(bot, skillId);
        if (AgentSkillAttackPlanner.skillAmmoReadiness(
                effect.getBulletCount(),
                effect.getBulletConsume(),
                AgentCombatHitCounter.shadowPartnerHitMultiplier(bot, route),
                route,
                () -> AgentCombatAmmoCounter.countAmmo(bot, weaponType))
                != AgentSkillAttackPlanner.SkillAmmoReadiness.READY) {
            return null;
        }

        String action = AgentAttackExecutionProvider.resolveSkillAttackAction(bot, skill, skillLevel, weaponType);
        if (AgentCombatSkillHitboxPolicy.isStrikePointAnchoredAoeSkill(skillId)) {
            Monster strikePointFallback = primaryTarget;
            primaryTarget = bot == null ? primaryTarget : AgentCombatTargetSelector.resolveStrikePointPrimaryByBasicWeapon(
                    bot.getPosition(),
                    strikePointFallback,
                    route,
                    facingLeft -> AgentCombatRangePolicy.basicWeaponReachRect(bot, facingLeft, route),
                    hitBox -> AgentCombatTargetSelector.resolveEffectivePrimary(
                            bot.getPosition(), strikePointFallback, hitBox, bot.getMap().getAllMonsters()));
        }

        Rectangle hitBox = AgentCombatSkillHitboxPolicy.calculateSkillHitBox(
                effect, bot, primaryTarget, route, skillId, action);
        if (hitBox == null) {
            return null;
        }

        boolean strikePointAnchored = AgentCombatSkillHitboxPolicy.isStrikePointAnchoredAoeSkill(skillId);
        Monster preSelectionPrimaryTarget = primaryTarget;
        AgentSkillAttackPlanner.SkillPrimaryTargetSelection targetSelection =
                AgentSkillAttackPlanner.resolvePrimaryTargetAfterHitbox(
                        strikePointAnchored,
                        preSelectionPrimaryTarget,
                        hitBox,
                        () -> AgentCombatRangePolicy.isPrimaryReachableByBasicWeapon(
                                bot, preSelectionPrimaryTarget, route),
                        (candidate, candidateHitBox) -> AgentCombatTargetSelector.resolveEffectivePrimary(
                                bot.getPosition(), candidate, candidateHitBox, bot.getMap().getAllMonsters()),
                        AgentCombatHitboxIntersection::intersectsMonster);
        if (targetSelection == null) {
            return null;
        }
        primaryTarget = targetSelection.target();

        int attackCount = AgentCombatHitCounter.effectiveHitCount(effect)
                * AgentCombatHitCounter.shadowPartnerHitMultiplier(bot, route);
        if (!AgentAttackExecutionProvider.canUseRangedAttackRoute(
                route, weaponType, bot.getPosition(), primaryTarget.getPosition())) {
            return null;
        }

        AgentAttackExecutionProvider.BasicAttackData fallbackAttackData =
                AgentAttackExecutionProvider.buildBasicAttackData(bot, primaryTarget.getPosition());
        AgentAttackDataProvider.AttackAnimationSpec attackSpec =
                AgentAttackDataProvider.getInstance().getBasicAttackSpec(weaponType);
        String fallbackAction = attackSpec.primaryAction();
        AgentSkillAttackPlanner.SkillAttackPacketFields packetFields =
                AgentSkillAttackPlanner.resolveSkillAttackPacketFields(
                        route, weaponType, bot.getPosition(), primaryTarget.getPosition(), action, fallbackAction);
        AgentAttackExecutionProvider.SkillAttackTiming skillTiming =
                AgentAttackExecutionProvider.resolveSkillAttackTiming(skill, action, bot, fallbackAttackData);
        List<Monster> targets = AgentCombatTargetSelector.collectTargetsInHitBox(
                primaryTarget, hitBox, Math.max(1, effect.getMobCount()), bot.getMap().getAllMonsters());
        if (skillId == DragonKnight.DRAGON_ROAR
                && !AgentCombatSupportPolicy.canUseDragonRoarPlan(
                bot, targets.size(),
                AgentCombatSupportPolicy.hasNearbyHealSkillAlly(
                        bot, config.SUPPORT_RANGE, config.SUPPORT_VERTICAL_RANGE))) {
            return null;
        }

        return new AgentAttackPlan(skillId, skillLevel, attackCount, hitBox, targets,
                route, packetFields.display(),
                packetFields.direction(), packetFields.rangedDirection(),
                packetFields.stance(),
                fallbackAttackData.speed(), skillTiming.hitDelayMs(), skillTiming.cooldownMs(),
                AgentCombatWeaponPolicy.damageWeaponTypeForAction(skillId, weaponType, action));
    }
}
