package server.agents.capabilities.combat;

import java.awt.Point;
import java.awt.Rectangle;
import server.agents.capabilities.combat.data.AgentMobHitboxProvider;
import server.life.Monster;

public final class AgentCombatHitboxIntersection {
    private AgentCombatHitboxIntersection() {
    }

    public static boolean intersectsMonster(Rectangle hitBox, Monster monster) {
        if (hitBox == null || monster == null) {
            return false;
        }

        Rectangle mobBounds = AgentMobHitboxProvider.getInstance().getMobBounds(monster);
        return intersectsMonsterBounds(hitBox, mobBounds, monster.getPosition());
    }

    public static boolean intersectsMonsterBounds(Rectangle hitBox, Rectangle mobBounds, Point monsterPosition) {
        if (hitBox == null) {
            return false;
        }
        if (mobBounds != null) {
            if (hitBox.intersects(mobBounds)) {
                return true;
            }
            // Preserve the legacy origin fallback for ordinary mobs whose authored body
            // straddles that origin. Fixed multipart mobs can publish an origin in empty space
            // between parts; treating it as hittable would permit attacks on an invisible point.
            return !AgentCombatTargetPositionPolicy.isHorizontallyDetached(
                    monsterPosition, mobBounds) && hitBox.contains(monsterPosition);
        }

        return monsterPosition != null && hitBox.contains(monsterPosition);
    }

    public static boolean isForwardProjectileHitBox(Rectangle hitBox, Point botPos) {
        if (hitBox == null || botPos == null) {
            return false;
        }
        return botPos.x < hitBox.getMinX() || botPos.x > hitBox.getMaxX();
    }
}
