package server.agents.capabilities.combat;

import java.awt.Point;
import server.agents.capabilities.movement.AgentMovementProfile;

public final class AgentCombatScoringPolicy {
    private AgentCombatScoringPolicy() {
    }

    public static double capDamageByCurrentHp(double expectedDamage, int currentHp) {
        if (currentHp <= 0) {
            return 0.0d;
        }
        return Math.min(expectedDamage, currentHp);
    }

    public static long estimateLocalTravelCostMs(Point from, Point to, AgentMovementProfile profile) {
        int dx = Math.abs(to.x - from.x);
        int dy = Math.abs(to.y - from.y);
        double walkVelocity = Math.max(1.0, profile.walkVelocityPxs());
        return Math.round(dx * 1000.0 / walkVelocity) + dy * 4L;
    }
}
