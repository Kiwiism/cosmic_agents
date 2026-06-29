package server.agents.capabilities.combat;

import client.inventory.WeaponType;
import java.awt.Rectangle;
import java.util.List;
import server.life.Monster;

public class AgentAttackPlan {
    public final int skillId;
    public final int skillLevel;
    public final int numDamage;
    public final Rectangle hitBox;
    public final List<Monster> targets;
    public final AgentAttackRoute route;
    public final int display;
    public final int direction;
    public final int rangedDirection;
    public final int stance;
    public final int speed;
    public final int hitDelayMs;
    public final int cooldownMs;
    public final WeaponType damageWeaponType;

    public AgentAttackPlan(int skillId, int skillLevel, int numDamage, Rectangle hitBox, List<Monster> targets,
                           AgentAttackRoute route, int display, int direction, int rangedDirection, int stance,
                           int speed, int hitDelayMs, int cooldownMs, WeaponType damageWeaponType) {
        this.skillId = skillId;
        this.skillLevel = skillLevel;
        this.numDamage = numDamage;
        this.hitBox = hitBox;
        this.targets = targets;
        this.route = route;
        this.display = display;
        this.direction = direction;
        this.rangedDirection = rangedDirection;
        this.stance = stance;
        this.speed = speed;
        this.hitDelayMs = hitDelayMs;
        this.cooldownMs = cooldownMs;
        this.damageWeaponType = damageWeaponType;
    }

    public boolean hasHitBox() {
        return hitBox != null;
    }

    public Monster primaryTarget() {
        return targets.get(0);
    }

    public boolean isCloseRangeRoute() {
        return route == AgentAttackRoute.CLOSE;
    }
}
