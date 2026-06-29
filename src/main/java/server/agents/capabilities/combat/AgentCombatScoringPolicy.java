package server.agents.capabilities.combat;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.life.Monster;

public final class AgentCombatScoringPolicy {
    public static final int LEGACY_AOE_CLUSTER_RADIUS_PX = 150;
    public static final long LEGACY_AOE_CLUSTER_BONUS_PER_MOB = 200L;

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

    public static long legacyAoeClusterBonus(Monster target,
                                             List<Monster> candidates,
                                             boolean hasMultiMobAoeSkill,
                                             int aoeMobCount) {
        return aoeClusterBonus(target, candidates, hasMultiMobAoeSkill, aoeMobCount,
                LEGACY_AOE_CLUSTER_RADIUS_PX, LEGACY_AOE_CLUSTER_BONUS_PER_MOB);
    }

    public static boolean isAoeSingleTargeting(int planSkillId,
                                               int planTargetCount,
                                               boolean hasMultiMobAoeSkill,
                                               int aoeSkillId,
                                               int aoeMobCount) {
        return hasMultiMobAoeSkill
                && planSkillId != aoeSkillId
                && planTargetCount < aoeMobCount;
    }

    public static int cappedAoeClusterSize(Monster anchor,
                                           Iterable<Monster> candidates,
                                           boolean hasMultiMobAoeSkill,
                                           int aoeMobCount,
                                           int clusterRadiusPx) {
        if (!hasMultiMobAoeSkill || anchor == null || anchor.getPosition() == null) {
            return 0;
        }
        return Math.min(clusterMonsters(anchor, candidates, clusterRadiusPx).size(),
                Math.max(1, aoeMobCount));
    }

    public static int legacyCappedAoeClusterSize(Monster anchor,
                                                 Iterable<Monster> candidates,
                                                 boolean hasMultiMobAoeSkill,
                                                 int aoeMobCount) {
        return cappedAoeClusterSize(anchor, candidates, hasMultiMobAoeSkill, aoeMobCount,
                LEGACY_AOE_CLUSTER_RADIUS_PX);
    }

    public static boolean aoeBeatsSingleTargetScore(int aoeDamage,
                                                    int aoeAttackCount,
                                                    int targetCount,
                                                    int bestSingleTargetDamage,
                                                    int bestSingleTargetHitCount) {
        return aoeBeatsSingleTargetScore(aoeDamage, aoeAttackCount, targetCount,
                bestSingleTargetScore(bestSingleTargetDamage, bestSingleTargetHitCount));
    }

    public static long bestSingleTargetScore(int bestSingleTargetDamage, int bestSingleTargetHitCount) {
        return Math.max(100L, (long) Math.max(0, bestSingleTargetDamage)
                * Math.max(1, bestSingleTargetHitCount));
    }

    public static boolean aoeBeatsSingleTargetScore(int aoeDamage,
                                                    int aoeAttackCount,
                                                    int targetCount,
                                                    long singleScore) {
        long aoeScore = (long) Math.max(0, aoeDamage)
                * Math.max(1, aoeAttackCount)
                * Math.max(1, targetCount);
        return aoeScore > singleScore;
    }

    public static List<Monster> clusterMonsters(Monster primaryTarget,
                                                Iterable<Monster> candidates,
                                                int clusterRadiusPx) {
        List<Monster> cluster = new ArrayList<>();
        if (primaryTarget == null || candidates == null || primaryTarget.getPosition() == null) {
            return cluster;
        }
        cluster.add(primaryTarget);
        Point targetPosition = primaryTarget.getPosition();
        long radiusSq = (long) clusterRadiusPx * clusterRadiusPx;
        for (Monster other : candidates) {
            if (other == primaryTarget || other == null || !other.isAlive() || other.getPosition() == null) {
                continue;
            }
            long dx = (long) other.getPosition().x - targetPosition.x;
            long dy = (long) other.getPosition().y - targetPosition.y;
            if (dx * dx + dy * dy <= radiusSq) {
                cluster.add(other);
            }
        }
        return cluster;
    }

    public static List<Monster> legacyClusterMonsters(Monster primaryTarget,
                                                      Iterable<Monster> candidates) {
        return clusterMonsters(primaryTarget, candidates, LEGACY_AOE_CLUSTER_RADIUS_PX);
    }

    public static Monster nearestMonster(List<Monster> monsters, int x, int y) {
        Monster best = null;
        long bestDistSq = Long.MAX_VALUE;
        for (Monster monster : monsters) {
            Point position = monster.getPosition();
            if (position == null) {
                continue;
            }
            long dx = (long) position.x - x;
            long dy = (long) position.y - y;
            long distSq = dx * dx + dy * dy;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = monster;
            }
        }
        return best;
    }

    public static int clusterCentroidX(List<Monster> cluster) {
        long sumX = 0L;
        for (Monster monster : cluster) {
            sumX += monster.getPosition().x;
        }
        return (int) (sumX / cluster.size());
    }

    public static int boundedRepositionShift(int centroidX, double hitBoxCenterX, int maxDistanceX) {
        int shift = centroidX - (int) Math.round(hitBoxCenterX);
        if (Math.abs(shift) > maxDistanceX) {
            return Integer.signum(shift) * maxDistanceX;
        }
        return shift;
    }

    public static boolean isWithinRepositionArrival(int shift, int arrivalX) {
        return Math.abs(shift) <= arrivalX;
    }
}
