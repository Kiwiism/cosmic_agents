package server.agents.capabilities.combat;

import java.util.function.BooleanSupplier;

public final class AgentSkillAttackPlanner {
    public enum SkillAttackReadiness {
        READY,
        MISSING_SKILL_ID,
        SKILL_COOLDOWN,
        SKILL_MISSING,
        SKILL_LEVEL_MISSING,
        CANNOT_PAY_COST,
        WEAPON_INCOMPATIBLE
    }

    private AgentSkillAttackPlanner() {
    }

    public static SkillAttackReadiness skillAttackReadiness(int skillId,
                                                            boolean skillCooling,
                                                            boolean skillExists,
                                                            int skillLevel,
                                                            BooleanSupplier canPaySkillCost,
                                                            BooleanSupplier canUseWithWeapon) {
        if (skillId == 0) {
            return SkillAttackReadiness.MISSING_SKILL_ID;
        }
        if (skillCooling) {
            return SkillAttackReadiness.SKILL_COOLDOWN;
        }
        if (!skillExists) {
            return SkillAttackReadiness.SKILL_MISSING;
        }
        if (skillLevel <= 0) {
            return SkillAttackReadiness.SKILL_LEVEL_MISSING;
        }
        if (!canPaySkillCost.getAsBoolean()) {
            return SkillAttackReadiness.CANNOT_PAY_COST;
        }
        if (!canUseWithWeapon.getAsBoolean()) {
            return SkillAttackReadiness.WEAPON_INCOMPATIBLE;
        }
        return SkillAttackReadiness.READY;
    }
}
