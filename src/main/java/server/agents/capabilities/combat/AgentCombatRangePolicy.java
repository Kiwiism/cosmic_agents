package server.agents.capabilities.combat;

import client.Character;
import client.inventory.WeaponType;
import java.awt.Point;
import java.awt.Rectangle;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.life.Monster;

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

    public static boolean isTargetInAttackRange(Rectangle attackHitBox,
                                                Monster target,
                                                Point agentPosition,
                                                Point targetPosition) {
        if (attackHitBox != null) {
            return AgentCombatHitboxIntersection.intersectsMonster(attackHitBox, target);
        }
        return isBasicAttackInRange(agentPosition, targetPosition);
    }

    public static boolean isTargetInAttackRange(AgentAttackPlan attackPlan, Character agent, Monster target) {
        if (attackPlan == null || agent == null || target == null) {
            return false;
        }
        return isTargetInAttackRange(
                attackPlan.hasHitBox() ? attackPlan.hitBox : null,
                target,
                agent.getPosition(),
                target.getPosition());
    }

    public static Rectangle basicWeaponReachRect(Character bot, boolean facingLeft, AgentAttackRoute route) {
        if (bot == null || bot.getPosition() == null) {
            return null;
        }
        if (route == AgentAttackRoute.RANGED) {
            return AgentProjectileHitbox.clientProjectileHitBox(bot, facingLeft, 1.0f);
        }
        if (route == AgentAttackRoute.CLOSE) {
            Point origin = bot.getPosition();
            int left = facingLeft ? origin.x - AgentCombatConfig.cfg.ATTACK_RANGE_X : origin.x;
            int top = origin.y - AgentCombatConfig.cfg.ATTACK_RANGE_Y;
            int height = AgentCombatConfig.cfg.ATTACK_RANGE_Y + AgentCombatConfig.cfg.ATTACK_DOWN_MAX;
            return new Rectangle(left, top, AgentCombatConfig.cfg.ATTACK_RANGE_X, height);
        }
        return null;
    }

    public static boolean isPrimaryReachableByBasicWeapon(Character bot, Monster target, AgentAttackRoute route) {
        if (bot == null || target == null || bot.getPosition() == null || target.getPosition() == null) {
            return false;
        }
        if (route != AgentAttackRoute.RANGED && route != AgentAttackRoute.CLOSE) {
            return true;
        }
        boolean facingLeft = target.getPosition().x < bot.getPosition().x;
        Rectangle basicReach = basicWeaponReachRect(bot, facingLeft, route);
        return basicReach != null && AgentCombatHitboxIntersection.intersectsMonster(basicReach, target);
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
