package server.agents.capabilities.combat;

import client.Character;
import client.Skill;
import client.SkillFactory;
import client.inventory.WeaponType;
import server.StatEffect;
import server.combat.CombatFormulaProvider;

public final class AgentAttackDamageProfileService {
    private AgentAttackDamageProfileService() {
    }

    public static CombatFormulaProvider.DamageProfile resolve(Character agent, AgentAttackPlan plan) {
        WeaponType equippedWeaponType = AgentAttackExecutionProvider.getEquippedWeaponType(agent);
        if (plan.route == AgentAttackRoute.CLOSE
                && AgentAttackExecutionProvider.isDegenerateCapableRangedWeapon(equippedWeaponType)) {
            Skill skill = plan.skillId != 0 ? SkillFactory.getSkill(plan.skillId) : null;
            StatEffect effect = skill != null && plan.skillLevel > 0 ? skill.getEffect(plan.skillLevel) : null;
            return CombatFormulaProvider.getInstance()
                    .resolveDegenerateDamageProfile(agent, equippedWeaponType, effect);
        }
        return CombatFormulaProvider.getInstance().resolveDamageProfile(
                agent, plan.skillId, plan.skillLevel,
                plan.route == AgentAttackRoute.MAGIC, plan.damageWeaponType);
    }
}
