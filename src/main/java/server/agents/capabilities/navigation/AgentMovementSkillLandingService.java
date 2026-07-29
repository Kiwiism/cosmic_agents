package server.agents.capabilities.navigation;

import server.agents.capabilities.movement.AgentMovementSkillConfig;
import server.maps.Foothold;
import server.maps.MapleMap;

import java.awt.Point;

/** Deterministic landing resolver shared by Teleport graph authoring and execution. */
public final class AgentMovementSkillLandingService {
    private AgentMovementSkillLandingService() {
    }

    public record Landing(Point point, int footholdId) {
        public Landing {
            point = new Point(point);
        }
    }

    public static Landing resolveTeleportLanding(MapleMap map,
                                                  Point origin,
                                                  int directionX,
                                                  int directionY) {
        if (map == null || map.getFootholds() == null || origin == null
                || (directionX == 0) == (directionY == 0)) {
            return null;
        }

        int range = AgentMovementSkillConfig.TELEPORT_RANGE_PX;
        int snapY = AgentMovementSkillConfig.TELEPORT_Y_SNAP_PX;
        Landing best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Foothold foothold : map.getFootholds().getAllFootholds()) {
            if (foothold == null || foothold.isWall()) {
                continue;
            }
            if (directionX != 0) {
                int targetX = origin.x + directionX * range;
                if (targetX < Math.min(foothold.getX1(), foothold.getX2())
                        || targetX > Math.max(foothold.getX1(), foothold.getX2())) {
                    continue;
                }
                int targetY = surfaceY(foothold, targetX);
                if (Math.abs(targetY - origin.y) > snapY) {
                    continue;
                }
                int distance = Math.abs(targetX - origin.x) + Math.abs(targetY - origin.y);
                if (distance < bestDistance) {
                    best = new Landing(new Point(targetX, targetY), foothold.getId());
                    bestDistance = distance;
                }
                continue;
            }

            if (origin.x < Math.min(foothold.getX1(), foothold.getX2())
                    || origin.x > Math.max(foothold.getX1(), foothold.getX2())) {
                continue;
            }
            int targetY = surfaceY(foothold, origin.x);
            int deltaY = targetY - origin.y;
            if (deltaY == 0 || Integer.signum(deltaY) != directionY || Math.abs(deltaY) > range) {
                continue;
            }
            int distance = Math.abs(deltaY);
            if (distance < bestDistance) {
                best = new Landing(new Point(origin.x, targetY), foothold.getId());
                bestDistance = distance;
            }
        }
        return best;
    }

    private static int surfaceY(Foothold foothold, int x) {
        if (foothold.getX1() == foothold.getX2()) {
            return Math.min(foothold.getY1(), foothold.getY2());
        }
        double ratio = (x - foothold.getX1()) / (double) (foothold.getX2() - foothold.getX1());
        return (int) Math.round(foothold.getY1()
                + (foothold.getY2() - foothold.getY1()) * ratio);
    }
}
