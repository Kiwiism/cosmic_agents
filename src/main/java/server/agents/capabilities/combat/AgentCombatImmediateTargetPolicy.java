package server.agents.capabilities.combat;

import client.Character;
import client.Skill;
import client.SkillFactory;
import client.inventory.WeaponType;
import java.awt.Point;
import java.awt.Rectangle;
import server.StatEffect;
import server.life.Monster;

public final class AgentCombatImmediateTargetPolicy {
    private AgentCombatImmediateTargetPolicy() {
    }

    public static boolean isImmediateProjectileTarget(Character agent,
                                                      Monster target,
                                                      boolean noAmmo,
                                                      int cachedAttackSkillId) {
        if (noAmmo || agent == null || target == null || !target.isAlive()) {
            return false;
        }

        Point agentPosition = agent.getPosition();
        Point targetPosition = target.getPosition();
        WeaponType weaponType = AgentAttackExecutionProvider.getEquippedWeaponType(agent);
        if (AgentAttackExecutionProvider.determineBasicWeaponRoute(weaponType) == AgentAttackRoute.RANGED
                && !AgentAttackExecutionProvider.shouldDegenerateRangedAttack(
                weaponType, agentPosition, targetPosition)) {
            Rectangle hitBox = AgentProjectileHitbox.clientProjectileHitBox(
                    agent, targetPosition.x < agentPosition.x, 1.0f);
            if (AgentCombatHitboxIntersection.intersectsMonster(hitBox, target)) {
                return true;
            }
        }

        return isImmediateProjectileSkillTarget(agent, target, cachedAttackSkillId);
    }

    public static boolean isImmediateProjectileSkillTarget(Character agent,
                                                           Monster target,
                                                           int cachedAttackSkillId) {
        if (agent == null || target == null || cachedAttackSkillId == 0
                || agent.skillIsCooling(cachedAttackSkillId)) {
            return false;
        }

        Skill skill = SkillFactory.getSkill(cachedAttackSkillId);
        int skillLevel = skill == null ? 0 : agent.getSkillLevel(skill);
        if (skillLevel <= 0) {
            return false;
        }

        StatEffect effect = skill.getEffect(skillLevel);
        if (effect == null || !effect.canPaySkillCost(agent)) {
            return false;
        }

        AgentAttackRoute route = AgentAttackExecutionProvider.determineSkillRoute(agent, cachedAttackSkillId);
        if (route != AgentAttackRoute.RANGED && route != AgentAttackRoute.MAGIC) {
            return false;
        }

        Rectangle hitBox = AgentCombatSkillHitboxPolicy.calculateSkillHitBox(
                effect, agent, target, route, cachedAttackSkillId, null);
        if (hitBox == null || !AgentCombatHitboxIntersection.intersectsMonster(hitBox, target)) {
            return false;
        }

        WeaponType weaponType = AgentAttackExecutionProvider.getEquippedWeaponType(agent);
        return AgentAttackExecutionProvider.canUseRangedAttackRoute(
                route, weaponType, agent.getPosition(), target.getPosition());
    }
}
