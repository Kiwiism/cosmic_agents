package server.agents.capabilities.combat;

import client.Character;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import server.life.Monster;
import server.maps.MapObject;

public final class AgentCombatTargetSelector {
    private AgentCombatTargetSelector() {
    }

    public static List<Monster> collectTargetsInHitBox(Monster primaryTarget,
                                                       Rectangle hitBox,
                                                       int maxTargets,
                                                       Iterable<Monster> candidates) {
        if (!AgentCombatHitboxIntersection.intersectsMonster(hitBox, primaryTarget)) {
            return List.of();
        }

        List<Monster> targets = new ArrayList<>();
        targets.add(primaryTarget);
        if (maxTargets <= 1) {
            return targets;
        }

        List<Monster> secondaryTargets = new ArrayList<>();
        for (Monster monster : candidates) {
            if (!AgentCombatTargetEligibilityPolicy.isHostileLivingMonster(monster)
                    || monster.getObjectId() == primaryTarget.getObjectId()) {
                continue;
            }
            if (!AgentCombatHitboxIntersection.intersectsMonster(hitBox, monster)) {
                continue;
            }
            secondaryTargets.add(monster);
        }

        secondaryTargets.sort(Comparator.comparingDouble(
                monster -> monster.getPosition().distanceSq(primaryTarget.getPosition())));
        for (Monster monster : secondaryTargets) {
            targets.add(monster);
            if (targets.size() >= maxTargets) {
                break;
            }
        }
        return targets;
    }

    public static List<Monster> aliveMonstersInRange(Iterable<Monster> candidates,
                                                     Point origin,
                                                     double rangeSq) {
        List<Monster> targets = new ArrayList<>();
        for (Monster monster : candidates) {
            if (AgentCombatTargetEligibilityPolicy.isHostileLivingMonster(monster)
                    && monster.getPosition().distanceSq(origin) <= rangeSq) {
                targets.add(monster);
            }
        }
        return targets;
    }

    public static List<Monster> aliveMonstersInRange(Character agent, Point origin, double rangeSq) {
        return aliveMonstersInRange(agent.getMap().getAllMonsters(), origin, rangeSq);
    }

    public static Monster resolveEffectivePrimary(Point origin,
                                                  Monster fallback,
                                                  Rectangle hitBox,
                                                  Iterable<Monster> candidates) {
        if (!AgentCombatHitboxIntersection.isForwardProjectileHitBox(hitBox, origin)) {
            return fallback;
        }
        Monster closest = null;
        double closestDistSq = Double.MAX_VALUE;
        for (Monster monster : candidates) {
            if (!AgentCombatTargetEligibilityPolicy.isHostileLivingMonster(monster)
                    || !AgentCombatHitboxIntersection.intersectsMonster(hitBox, monster)) {
                continue;
            }
            double distSq = monster.getPosition().distanceSq(origin);
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = monster;
            }
        }
        return closest != null ? closest : fallback;
    }

    public static Monster findClosestAliveMonster(Iterable<Monster> candidates,
                                                  Point origin,
                                                  double maxRangeSq) {
        Monster closest = null;
        double closestDistSq = maxRangeSq;
        for (Monster monster : candidates) {
            if (!AgentCombatTargetEligibilityPolicy.isHostileLivingMonster(monster)) {
                continue;
            }
            double distSq = monster.getPosition().distanceSq(origin);
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = monster;
            }
        }
        return closest;
    }

    public static Monster findReachableOnOppositeFacing(Point agentPosition,
                                                        Monster originalTarget,
                                                        Function<Point, Rectangle> oppositeHitBox,
                                                        Function<Rectangle, Monster> effectivePrimary) {
        if (agentPosition == null || originalTarget == null || originalTarget.getPosition() == null) {
            return null;
        }

        Point mirroredPosition = new Point(2 * agentPosition.x - originalTarget.getPosition().x,
                originalTarget.getPosition().y);
        Rectangle hitBox = oppositeHitBox.apply(mirroredPosition);
        if (hitBox == null) {
            return null;
        }

        Monster mirrored = effectivePrimary.apply(hitBox);
        return mirrored != originalTarget ? mirrored : null;
    }

    public static Monster resolveStrikePointPrimaryByBasicWeapon(Point agentPosition,
                                                                 Monster fallback,
                                                                 AgentAttackRoute route,
                                                                 Function<Boolean, Rectangle> basicReach,
                                                                 Function<Rectangle, Monster> effectivePrimary) {
        if (agentPosition == null || fallback == null || fallback.getPosition() == null) {
            return fallback;
        }
        if (route != AgentAttackRoute.RANGED && route != AgentAttackRoute.CLOSE) {
            return fallback;
        }

        boolean facingLeft = fallback.getPosition().x < agentPosition.x;
        Rectangle hitBox = basicReach.apply(facingLeft);
        if (hitBox == null) {
            return fallback;
        }
        return effectivePrimary.apply(hitBox);
    }

    public static List<Monster> collectUndeadMobsInHealRange(Rectangle bounds,
                                                             Iterable<MapObject> objects,
                                                             int cap) {
        if (bounds == null) {
            return new ArrayList<>();
        }

        List<Monster> undead = new ArrayList<>();
        for (MapObject object : objects) {
            Monster monster = (Monster) object;
            if (monster.isAlive() && monster.getStats().isUndead()) {
                undead.add(monster);
                if (undead.size() >= cap) {
                    break;
                }
            }
        }
        return undead;
    }
}
