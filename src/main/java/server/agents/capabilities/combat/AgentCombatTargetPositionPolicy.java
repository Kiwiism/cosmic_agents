package server.agents.capabilities.combat;

import client.Character;
import server.agents.capabilities.combat.data.AgentMobHitboxProvider;
import server.life.Monster;

import java.awt.Point;
import java.awt.Rectangle;

/** Resolves a physical approach point when a mob's server origin is not on its visible body. */
public final class AgentCombatTargetPositionPolicy {
    private AgentCombatTargetPositionPolicy() {
    }

    public static Point approachPoint(Character agent, Monster target) {
        if (target == null) {
            return null;
        }
        Point origin = target.getPosition();
        Rectangle visibleBody = AgentMobHitboxProvider.getInstance().getMobBounds(target);
        if (isHorizontallyDetached(origin, visibleBody)) {
            Point source = agent == null ? null : agent.getPosition();
            return AgentCombatAimPointPolicy.nearestPoint(source, visibleBody, origin);
        }
        return origin;
    }

    /**
     * Fixed multipart monsters may publish an origin between their visible parts. In that case
     * navigation must approach the WZ-authored body rectangle instead of the invisible origin.
     */
    static boolean isHorizontallyDetached(Point origin, Rectangle visibleBody) {
        if (origin == null || visibleBody == null || visibleBody.isEmpty()) {
            return false;
        }
        int right = visibleBody.x + visibleBody.width;
        return origin.x < visibleBody.x || origin.x >= right;
    }
}
