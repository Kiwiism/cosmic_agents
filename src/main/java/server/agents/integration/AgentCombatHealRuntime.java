package server.agents.integration;

import server.agents.capabilities.combat.AgentCombatSkillCacheStateRuntime;

import server.agents.capabilities.combat.AgentCombatBuffStateRuntime;

import server.agents.capabilities.combat.AgentCombatCooldownStateRuntime;

import server.agents.capabilities.movement.AgentJumpActionService;
import server.agents.capabilities.movement.AgentMovementBroadcastService;

import client.Character;
import client.Skill;
import client.SkillFactory;
import net.server.channel.handlers.AbstractDealDamageHandler;
import server.StatEffect;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentAttackRoute;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.combat.AgentCombatSupportPolicy;
import server.agents.capabilities.combat.AgentCombatTargetSelector;
import server.agents.runtime.AgentFollowAnchorService;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.combat.CombatFormulaProvider;
import server.life.Monster;

import java.awt.Rectangle;
import java.util.HashMap;
import java.util.List;

public final class AgentCombatHealRuntime {
    private AgentCombatHealRuntime() {
    }

    public static boolean tickSupportHealing(AgentRuntimeEntry entry, Character bot, AgentCombatConfig.Config config) {
        int healSkillId = AgentCombatSkillCacheStateRuntime.healSkillId(entry);
        if (!AgentCombatSupportPolicy.shouldTickSupportHealing(
                AgentCombatCooldownStateRuntime.blocksGroundedAttack(entry, AgentMovementStateRuntime.inAir(entry)),
                AgentCombatBuffStateRuntime.supportHealsEnabled(entry),
                AgentModeStateRuntime.following(entry),
                AgentModeStateRuntime.grinding(entry),
                healSkillId,
                bot.skillIsCooling(healSkillId))) {
            return false;
        }

        Skill skill = SkillFactory.getSkill(healSkillId);
        int lvl = bot.getSkillLevel(skill);
        if (lvl <= 0) return false;
        StatEffect fx = skill.getEffect(lvl);

        Rectangle healBounds = fx.hasBoundingBox()
                ? fx.calculateBoundingBox(bot.getPosition(), bot.isFacingLeft())
                : null;
        boolean selfNeedsHeal = AgentCombatSupportPolicy.needsHeal(bot, config.SUPPORT_HEAL_TARGET_RATIO);
        boolean partyNeedsHeal = selfNeedsHeal || AgentCombatSupportPolicy.hasPartyMemberInBoundsNeedingHeal(
                bot,
                healBounds,
                config.SUPPORT_RANGE,
                config.SUPPORT_VERTICAL_RANGE,
                config.SUPPORT_HEAL_TARGET_RATIO);
        List<Monster> undeadTargets = AgentCombatTargetSelector.collectUndeadMobsInHealRange(bot, fx, healBounds);
        if (!AgentCombatSupportPolicy.shouldCastSupportHeal(partyNeedsHeal, !undeadTargets.isEmpty())) return false;

        boolean jumpHealing = false;
        if (AgentModeStateRuntime.following(entry)
                && AgentMovementStateRuntime.grounded(entry)
                && AgentMovementStateRuntime.notClimbing(entry)
                && config.JUMP_HEAL_LEADER_AHEAD_PX > 0) {
            Character leader = AgentRuntimeIdentityRuntime.owner(entry);
            Character anchor = AgentFollowAnchorService.resolve(
                    entry,
                    leader,
                    leader == null ? List.of() : AgentSessionLifecycleSideEffects.getBotEntries(leader.getId()));
            if (anchor != null && anchor != bot && anchor.getMap() == bot.getMap()) {
                int dx = anchor.getPosition().x - bot.getPosition().x;
                if (Math.abs(dx) >= config.JUMP_HEAL_LEADER_AHEAD_PX) {
                    AgentJumpActionService.initiateJump(entry, bot, dx);
                    jumpHealing = true;
                }
            }
        }

        if (!fx.canPaySkillCost(bot) || !fx.applyTo(bot)) return false;

        long now = System.currentTimeMillis();
        AgentAttackExecutionProvider.BasicAttackData fallbackAttackData =
                AgentAttackExecutionProvider.buildBasicAttackData(bot, bot.getPosition());
        String action = AgentAttackExecutionProvider.resolveSkillAttackAction(bot, skill, lvl,
                AgentAttackExecutionProvider.getEquippedWeaponType(bot));
        AgentAttackExecutionProvider.SkillAttackTiming skillTiming =
                AgentAttackExecutionProvider.resolveSkillAttackTiming(skill, action, bot, fallbackAttackData);
        AgentCombatCooldownStateRuntime.maxAttackCooldown(entry, skillTiming.cooldownMs());
        if (partyNeedsHeal && fx.getCooldown() > 0) {
            bot.addCooldown(healSkillId, now, fx.getCooldown() * 1000L);
        }

        sendHealAttack(healSkillId, lvl, bot, undeadTargets, fallbackAttackData, skillTiming);
        AgentCombatAlertRuntime.markAlerted(entry);
        AgentCombatCooldownStateRuntime.maxMoveWindow(entry, config.HEAL_MOVE_WINDOW_MS);
        if (!jumpHealing) {
            AgentMovementStateRuntime.clearMoveDirection(entry);
            AgentMovementBroadcastService.broadcastMovement(entry);
        }
        return true;
    }

    private static void sendHealAttack(int healSkillId, int lvl, Character bot,
            List<Monster> undeadTargets,
            AgentAttackExecutionProvider.BasicAttackData fallbackAttackData,
            AgentAttackExecutionProvider.SkillAttackTiming skillTiming) {
        AgentAttackRoute route = AgentAttackExecutionProvider.determineSkillRoute(bot, healSkillId);
        int healTargetCount = Math.max(1, undeadTargets.size() + 1);
        CombatFormulaProvider.DamageProfile damageProfile = CombatFormulaProvider.getInstance()
                .resolveDamageProfile(bot, healSkillId, lvl, true, healTargetCount);
        AbstractDealDamageHandler.AttackInfo attack = new AbstractDealDamageHandler.AttackInfo();
        attack.skill = healSkillId;
        attack.skilllevel = lvl;
        attack.numDamage = 1;
        attack.numAttacked = undeadTargets.size();
        attack.numAttackedAndDamage = (undeadTargets.size() << 4) | 1;
        attack.speed = fallbackAttackData.speed();
        boolean facingLeft = bot.isFacingLeft();
        AgentAttackExecutionProvider.CloseRangePacketFields castFields =
                AgentAttackExecutionProvider.mimicCloseRangePacketFields("alert2", "alert2", facingLeft);
        attack.display = castFields.display();
        attack.direction = castFields.bodyActionId();
        attack.stance = AgentAttackExecutionProvider.attackPacketStance(facingLeft);
        attack.rangedirection = AgentAttackExecutionProvider.attackPacketStance(facingLeft);
        attack.ranged = false;
        attack.magic = damageProfile.magicAttack();
        attack.targets = new HashMap<>();
        for (Monster target : undeadTargets) {
            attack.targets.put(target.getObjectId(),
                    CombatFormulaProvider.getInstance().makeTarget(
                            bot, target, 1, healSkillId, damageProfile, skillTiming.hitDelayMs()));
        }
        AgentAttackExecutionProvider.applyAttackRoute(route, attack, bot);
    }
}
