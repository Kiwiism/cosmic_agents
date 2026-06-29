package server.agents.capabilities.combat;

import java.util.function.BooleanSupplier;

public final class AgentCombatAttackExecutionPolicy {
    public enum AttackExecutionReadiness {
        READY,
        ATTACK_COOLDOWN,
        NO_AMMO,
        CANNOT_USE_SKILL,
        CANNOT_USE_ATTACK_PLAN
    }

    private AgentCombatAttackExecutionPolicy() {
    }

    public static AttackExecutionReadiness attackExecutionReadiness(boolean attackCooldownActive,
                                                                    boolean noAmmo,
                                                                    int skillId,
                                                                    BooleanSupplier canUseSkill,
                                                                    BooleanSupplier canUseAttackPlanNow) {
        if (attackCooldownActive) {
            return AttackExecutionReadiness.ATTACK_COOLDOWN;
        }
        if (noAmmo) {
            return AttackExecutionReadiness.NO_AMMO;
        }
        if (skillId != 0 && !canUseSkill.getAsBoolean()) {
            return AttackExecutionReadiness.CANNOT_USE_SKILL;
        }
        if (!canUseAttackPlanNow.getAsBoolean()) {
            return AttackExecutionReadiness.CANNOT_USE_ATTACK_PLAN;
        }
        return AttackExecutionReadiness.READY;
    }
}
