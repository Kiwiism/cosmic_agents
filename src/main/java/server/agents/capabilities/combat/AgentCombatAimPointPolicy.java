package server.agents.capabilities.combat;

import client.Character;
import server.agents.capabilities.combat.data.AgentMobHitboxProvider;
import server.life.Monster;

import java.awt.Point;
import java.awt.Rectangle;

/** Resolves the nearest visible point of a large or multipart mob for facing/range checks. */
public final class AgentCombatAimPointPolicy {
    private AgentCombatAimPointPolicy() {
    }

    public static Point aimPoint(Character agent, Monster target) {
        if (target == null) {
            return null;
        }
        Point fallback = target.getPosition();
        if (agent == null || agent.getPosition() == null || fallback == null) {
            return fallback == null ? null : new Point(fallback);
        }
        Rectangle bounds = AgentMobHitboxProvider.getInstance().getMobBounds(target);
        return nearestPoint(agent.getPosition(), bounds, fallback);
    }

    static Point nearestPoint(Point source, Rectangle bounds, Point fallback) {
        if (source == null || bounds == null || bounds.isEmpty()) {
            return fallback == null ? null : new Point(fallback);
        }
        int right = bounds.x + Math.max(0, bounds.width - 1);
        int bottom = bounds.y + Math.max(0, bounds.height - 1);
        return new Point(
                Math.max(bounds.x, Math.min(source.x, right)),
                Math.max(bounds.y, Math.min(source.y, bottom)));
    }
}
