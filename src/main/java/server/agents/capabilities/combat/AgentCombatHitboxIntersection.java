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
            return hitBox.intersects(mobBounds) || hitBox.contains(monsterPosition);
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
