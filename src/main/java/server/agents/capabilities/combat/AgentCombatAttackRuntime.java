package server.agents.capabilities.combat;

import server.agents.capabilities.supplies.AgentAmmoStateRuntime;

import client.Character;
import client.Disease;
import net.server.channel.handlers.AbstractDealDamageHandler;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.operations.events.AgentAttackResolvedEvent;
import server.agents.integration.CombatAttackApplicationResult;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSessionEventRuntime;
import server.combat.CombatFormulaProvider;
import server.life.Monster;
import server.maps.MapleMap;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class AgentCombatAttackRuntime {
    private AgentCombatAttackRuntime() {
    }

    public static AgentAttackTransactionResult attackMonster(AgentRuntimeEntry entry,
                                                              Character bot,
                                                              AgentAttackPlan attackPlan) {
        if (entry == null || bot == null || attackPlan == null || attackPlan.targets == null
                || attackPlan.targets.isEmpty() || attackPlan.route == null) {
            return AgentAttackTransactionResult.rejected(
                    AgentAttackTransactionResult.Reason.INVALID_REQUEST, mapId(bot), skillId(attackPlan));
        }
        Monster primaryTarget = attackPlan.targets.get(0);
        if (primaryTarget == null || !primaryTarget.isAlive()) {
            return AgentAttackTransactionResult.rejected(
                    AgentAttackTransactionResult.Reason.TARGET_UNAVAILABLE, mapId(bot), attackPlan.skillId);
        }
        AgentCombatAttackExecutionPolicy.AttackExecutionReadiness readiness =
                AgentCombatAttackExecutionPolicy.attackExecutionReadiness(
                        AgentCombatCooldownStateRuntime.hasAttackCooldown(entry),
                        AgentAmmoStateRuntime.noAmmo(entry) && attackPlan.route == AgentAttackRoute.RANGED,
                        attackPlan.skillId,
                        () -> AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                                        attackPlan.skillId,
                                        AgentAttackExecutionProvider.getEquippedWeaponType(bot))
                                && (attackPlan.skillId <= 0 || !bot.hasDisease(Disease.SEAL))
                                && AgentCombatSkillUsePolicy.canPaySkillCost(
                                        bot, attackPlan.skillId, attackPlan.skillLevel),
                        () -> entry != null && attackPlan != null && AgentCombatRangePolicy.canUseAttackPlanNow(
                                AgentMovementStateRuntime.grounded(entry),
                                AgentAttackExecutionProvider.getEquippedWeaponType(bot),
                                attackPlan.route));
        if (readiness != AgentCombatAttackExecutionPolicy.AttackExecutionReadiness.READY) {
            return AgentAttackTransactionResult.deferred(
                    reason(readiness), bot.getMapId(), attackPlan.skillId);
        }

        MapleMap attackMap = bot.getMap();
        if (primaryTarget.getMap() != attackMap) {
            return AgentAttackTransactionResult.rejected(
                    AgentAttackTransactionResult.Reason.TARGET_NOT_IN_AGENT_MAP,
                    bot.getMapId(), attackPlan.skillId);
        }

        int targetLimit = AgentAttackPacketPolicy.targetCount(attackPlan.targets.size());
        List<Monster> authoritativeTargets = authoritativeTargets(attackPlan.targets, targetLimit, attackMap);
        if (authoritativeTargets.isEmpty() || authoritativeTargets.getFirst() != primaryTarget) {
            return AgentAttackTransactionResult.rejected(
                    AgentAttackTransactionResult.Reason.TARGET_UNAVAILABLE, bot.getMapId(), attackPlan.skillId);
        }
        int numAttacked = authoritativeTargets.size();
        int numDamage = AgentAttackPacketPolicy.damageLineCount(attackPlan.numDamage);
        AbstractDealDamageHandler.AttackInfo attack = new AbstractDealDamageHandler.AttackInfo();
        attack.skill = attackPlan.skillId;
        attack.skilllevel = attackPlan.skillLevel;
        attack.numDamage = numDamage;
        attack.numAttacked = numAttacked;
        attack.numAttackedAndDamage = AgentAttackPacketPolicy.packCounts(numAttacked, numDamage);
        attack.speed = attackPlan.speed;
        attack.stance = attackPlan.stance;
        attack.display = attackPlan.display;
        attack.direction = attackPlan.direction;
        attack.rangedirection = attackPlan.rangedDirection;
        attack.ranged = attackPlan.route == AgentAttackRoute.RANGED;
        CombatFormulaProvider.DamageProfile damageProfile = AgentAttackDamageProfileService.resolve(bot, attackPlan);
        attack.magic = damageProfile.magicAttack();
        attack.targets = new HashMap<>();

        for (Monster target : authoritativeTargets) {
            attack.targets.put(target.getObjectId(),
                    CombatFormulaProvider.getInstance().makeTarget(bot, target, numDamage,
                            attackPlan.skillId, damageProfile, attackPlan.hitDelayMs));
        }

        if (bot.getMap() != attackMap) {
            return AgentAttackTransactionResult.deferred(
                    AgentAttackTransactionResult.Reason.MAP_CHANGED_DURING_ATTACK,
                    bot.getMapId(), attackPlan.skillId);
        }
        List<Monster> committedTargets = authoritativeTargets(authoritativeTargets, numAttacked, attackMap);
        if (committedTargets.isEmpty() || committedTargets.getFirst() != primaryTarget) {
            return AgentAttackTransactionResult.rejected(
                    AgentAttackTransactionResult.Reason.TARGET_UNAVAILABLE, bot.getMapId(), attackPlan.skillId);
        }
        if (committedTargets.size() != authoritativeTargets.size()) {
            Set<Integer> committedTargetIds = committedTargets.stream()
                    .map(Monster::getObjectId)
                    .collect(Collectors.toUnmodifiableSet());
            attack.targets.keySet().retainAll(committedTargetIds);
            attack.numAttacked = committedTargets.size();
            attack.numAttackedAndDamage = AgentAttackPacketPolicy.packCounts(committedTargets.size(), numDamage);
        }
        CombatAttackApplicationResult application =
                AgentAttackExecutionProvider.applyAttackRoute(attackPlan.route, attack, bot);
        if (application == null || !application.applied()) {
            return AgentAttackTransactionResult.rejected(
                    AgentAttackTransactionResult.Reason.HANDLER_REJECTED,
                    bot.getMapId(), attackPlan.skillId);
        }
        int hitLines = attack.targets.values().stream().flatMap(target -> target.damageLines().stream())
                .mapToInt(damage -> damage > 0 ? 1 : 0).sum();
        int totalLines = attack.targets.values().stream().mapToInt(target -> target.damageLines().size()).sum();
        long committedAtMs = System.currentTimeMillis();
        AgentSessionEventRuntime.bus(entry).publish(new AgentAttackResolvedEvent(
                bot.getId(), committedAtMs, bot.getMapId(), attack.targets.size(),
                hitLines, totalLines - hitLines));
        AgentCombatCooldownStateRuntime.maxAttackCooldown(entry, attackPlan.cooldownMs);
        AgentCombatFacingRuntime.rememberAttackFacing(entry, attackPlan.stance);
        AgentCombatAlertRuntime.markAlerted(entry);
        return AgentAttackTransactionResult.committed(
                bot.getMapId(),
                attackPlan.skillId,
                committedTargets.stream().map(Monster::getObjectId).toList(),
                hitLines,
                totalLines - hitLines,
                committedAtMs);
    }

    private static List<Monster> authoritativeTargets(List<Monster> candidates, int limit, MapleMap attackMap) {
        LinkedHashMap<Integer, Monster> targets = new LinkedHashMap<>();
        for (Monster candidate : candidates) {
            if (targets.size() >= limit) {
                break;
            }
            if (candidate != null && candidate.isAlive() && candidate.getMap() == attackMap) {
                targets.putIfAbsent(candidate.getObjectId(), candidate);
            }
        }
        return List.copyOf(targets.values());
    }

    private static AgentAttackTransactionResult.Reason reason(
            AgentCombatAttackExecutionPolicy.AttackExecutionReadiness readiness) {
        return switch (readiness) {
            case ATTACK_COOLDOWN -> AgentAttackTransactionResult.Reason.ATTACK_COOLDOWN;
            case NO_AMMO -> AgentAttackTransactionResult.Reason.NO_AMMO;
            case CANNOT_USE_SKILL -> AgentAttackTransactionResult.Reason.CANNOT_USE_SKILL;
            case CANNOT_USE_ATTACK_PLAN -> AgentAttackTransactionResult.Reason.CANNOT_USE_ATTACK_PLAN;
            case READY -> AgentAttackTransactionResult.Reason.NONE;
        };
    }

    private static int mapId(Character bot) {
        return bot == null ? 0 : bot.getMapId();
    }

    private static int skillId(AgentAttackPlan attackPlan) {
        return attackPlan == null ? 0 : attackPlan.skillId;
    }

}
