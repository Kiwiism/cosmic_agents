package server.agents.capabilities.combat;

import client.inventory.WeaponType;
import java.awt.Point;
import server.agents.capabilities.movement.AgentMovementProfile;

public final class AgentCombatRangePolicy {
    private AgentCombatRangePolicy() {
    }

    public static boolean isBasicAttackInRange(Point botPos, Point targetPos) {
        if (botPos == null || targetPos == null) {
            return false;
        }
        int dx = Math.abs(targetPos.x - botPos.x);
        int dy = botPos.y - targetPos.y;
        boolean inHorizontalRange = dx <= AgentCombatConfig.cfg.ATTACK_RANGE_X;
        boolean inVerticalRange = dy >= -AgentCombatConfig.cfg.ATTACK_DOWN_MAX
                && dy <= AgentCombatConfig.cfg.ATTACK_RANGE_Y;
        return inHorizontalRange && inVerticalRange;
    }

    public static boolean canUseAttackPlanNow(boolean grounded, WeaponType weaponType, AgentAttackRoute route) {
        if (grounded) {
            return true;
        }
        return !isAirborneRangedAttackBlockedWeapon(weaponType) || route != AgentAttackRoute.RANGED;
    }

    public static boolean isTargetJumpable(AgentMovementProfile movementProfile,
                                           boolean closeRangeRoute,
                                           Point botPos,
                                           Point targetPos,
                                           double maxJumpHeightPx) {
        if (!closeRangeRoute || botPos == null || targetPos == null) {
            return false;
        }

        int dx = Math.abs(targetPos.x - botPos.x);
        if (dx > AgentCombatConfig.cfg.ATTACK_RANGE_X + AgentCombatConfig.cfg.ATTACK_JUMP_X_EXTRA) {
            return false;
        }

        int dy = botPos.y - targetPos.y;
        int maxJumpHeight = Math.max(AgentCombatConfig.cfg.ATTACK_JUMP_Y, (int) Math.ceil(maxJumpHeightPx));
        return dy > AgentCombatConfig.cfg.ATTACK_RANGE_Y && dy <= maxJumpHeight;
    }

    public static boolean isAirborneRangedAttackBlockedWeapon(WeaponType weaponType) {
        return weaponType == WeaponType.BOW
                || weaponType == WeaponType.CROSSBOW
                || weaponType == WeaponType.GUN;
    }
}
