package server.agents.capabilities.combat;

import client.Character;
import client.Skill;
import client.SkillEligibilityPolicy;
import client.inventory.WeaponType;
import server.StatEffect;
import server.agents.integration.AgentSkillGatewayRuntime;
import server.agents.integration.SkillGateway;

import java.util.function.BooleanSupplier;

public final class AgentCombatSkillUsePolicy {
    private AgentCombatSkillUsePolicy() {
    }

    public static boolean canPaySkillCost(Character bot, int skillId, int skillLevel) {
        return canPaySkillCost(bot, skillId, skillLevel, AgentSkillGatewayRuntime.skills());
    }

    static boolean canPaySkillCost(Character bot, int skillId, int skillLevel, SkillGateway skills) {
        return canUseAtExecution(bot, skillId, skillLevel, () -> true, skills);
    }

    public static boolean canUseAtExecution(Character bot,
                                            int skillId,
                                            int skillLevel,
                                            BooleanSupplier weaponAndAmmoRequirements) {
        return canUseAtExecution(
                bot, skillId, skillLevel, weaponAndAmmoRequirements, AgentSkillGatewayRuntime.skills());
    }

    public static boolean canUseAttackAtExecution(Character bot,
                                                  int skillId,
                                                  int skillLevel,
                                                  AgentAttackRoute route) {
        Skill skill = AgentSkillGatewayRuntime.skills().getSkill(skillId);
        if (skill == null || skillLevel <= 0) {
            return false;
        }
        StatEffect effect = skill.getEffect(skillLevel);
        if (effect == null) {
            return false;
        }
        WeaponType weaponType = AgentAttackExecutionProvider.getEquippedWeaponType(bot);
        return SkillEligibilityPolicy.evaluate(
                bot,
                skill,
                skillLevel,
                false,
                () -> AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(skillId, weaponType)
                        && AgentSkillAttackPlanner.skillAmmoReadiness(
                                effect.getBulletCount(),
                                effect.getBulletConsume(),
                                AgentCombatHitCounter.shadowPartnerHitMultiplier(bot, route),
                                route,
                                () -> AgentCombatAmmoCounter.countAmmo(bot, weaponType))
                        == AgentSkillAttackPlanner.SkillAmmoReadiness.READY)
                .allowed();
    }

    static boolean canUseAtExecution(Character bot,
                                     int skillId,
                                     int skillLevel,
                                     BooleanSupplier weaponAndAmmoRequirements,
                                     SkillGateway skills) {
        Skill skill = skills.getSkill(skillId);
        if (skill == null || skillLevel <= 0) {
            return false;
        }
        return SkillEligibilityPolicy.evaluate(
                bot, skill, skillLevel, false, weaponAndAmmoRequirements).allowed();
    }
}
