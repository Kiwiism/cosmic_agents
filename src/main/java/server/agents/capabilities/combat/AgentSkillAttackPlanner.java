package server.agents.capabilities.combat;

import client.inventory.WeaponType;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import server.life.Monster;

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

    public enum SkillAmmoReadiness {
        READY,
        INSUFFICIENT_AMMO
    }

    public record SkillPrimaryTargetSelection(Monster target) {
    }

    public record SkillAttackPacketFields(int display, int direction, int rangedDirection, int stance) {
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

    public static SkillAmmoReadiness skillAmmoReadiness(int bulletCount,
                                                        int bulletConsume,
                                                        int hitMultiplier,
                                                        AgentAttackRoute route,
                                                        IntSupplier ammoCount) {
        int ammoCost = Math.max(bulletCount, bulletConsume) * Math.max(1, hitMultiplier);
        if (ammoCost > 0 && route == AgentAttackRoute.RANGED && ammoCount.getAsInt() < ammoCost) {
            return SkillAmmoReadiness.INSUFFICIENT_AMMO;
        }
        return SkillAmmoReadiness.READY;
    }

    public static SkillPrimaryTargetSelection resolvePrimaryTargetAfterHitbox(
            boolean strikePointAnchored,
            Monster primaryTarget,
            Rectangle hitBox,
            BooleanSupplier primaryReachableByBasicWeapon,
            BiFunction<Monster, Rectangle, Monster> resolveEffectivePrimary,
            BiPredicate<Rectangle, Monster> intersectsMonster) {
        if (strikePointAnchored && !primaryReachableByBasicWeapon.getAsBoolean()) {
            return null;
        }

        Monster resolvedTarget = primaryTarget;
        if (!strikePointAnchored) {
            resolvedTarget = resolveEffectivePrimary.apply(primaryTarget, hitBox);
        }
        if (!intersectsMonster.test(hitBox, resolvedTarget)) {
            return null;
        }
        return new SkillPrimaryTargetSelection(resolvedTarget);
    }

    public static SkillAttackPacketFields resolveSkillAttackPacketFields(AgentAttackRoute route,
                                                                         WeaponType weaponType,
                                                                         Point agentPosition,
                                                                         Point targetPosition,
                                                                         String action,
                                                                         String fallbackAction) {
        boolean facingLeft = targetPosition.x < agentPosition.x;
        if (route == AgentAttackRoute.CLOSE) {
            AgentAttackExecutionProvider.CloseRangePacketFields closeRangePacketFields =
                    AgentAttackExecutionProvider.mimicCloseRangePacketFields(action, fallbackAction, facingLeft);
            return new SkillAttackPacketFields(
                    closeRangePacketFields.display(),
                    closeRangePacketFields.bodyActionId(),
                    closeRangePacketFields.bodyActionId(),
                    AgentAttackExecutionProvider.attackPacketStance(facingLeft));
        }

        int direction = AgentAttackExecutionProvider.bodyActionId(action, fallbackAction, weaponType);
        return new SkillAttackPacketFields(
                0,
                direction,
                direction,
                AgentAttackExecutionProvider.attackPacketStance(facingLeft));
    }
}
