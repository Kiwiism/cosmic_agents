package server.agents.capabilities.combat;

import java.awt.Point;
import java.util.List;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.life.Monster;

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

    public static long localTargetScore(Point botPos,
                                        Point targetPos,
                                        boolean sameFoothold,
                                        int attackRangeY) {
        int dx = Math.abs(targetPos.x - botPos.x);
        int dy = Math.abs(targetPos.y - botPos.y);
        boolean nearSameLevel = dy <= attackRangeY;

        long score = dx;
        score += (long) dy * 8L;
        if (!nearSameLevel) {
            score += 600L;
        }
        if (!sameFoothold) {
            score += 1200L;
        }
        return score;
    }

    public static long aoeClusterBonus(Monster target,
                                       List<Monster> candidates,
                                       boolean hasMultiMobAoeSkill,
                                       int aoeMobCount,
                                       int clusterRadiusPx,
                                       long bonusPerMob) {
        if (!hasMultiMobAoeSkill || target == null || candidates == null || candidates.isEmpty()) {
            return 0L;
        }
        Point targetPosition = target.getPosition();
        if (targetPosition == null) {
            return 0L;
        }
        long radiusSq = (long) clusterRadiusPx * clusterRadiusPx;
        int cap = aoeMobCount - 1;
        int neighbors = 0;
        for (Monster other : candidates) {
            if (other == target || other == null || !other.isAlive()) {
                continue;
            }
            Point otherPosition = other.getPosition();
            if (otherPosition == null) {
                continue;
            }
            long dx = (long) otherPosition.x - targetPosition.x;
            long dy = (long) otherPosition.y - targetPosition.y;
            if (dx * dx + dy * dy <= radiusSq) {
                neighbors++;
                if (neighbors >= cap) {
                    break;
                }
            }
        }
        return neighbors * bonusPerMob;
    }
}
