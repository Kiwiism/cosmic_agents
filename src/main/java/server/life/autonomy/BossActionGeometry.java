package server.life.autonomy;

import java.awt.Point;
import java.util.List;

/** Coordinate helpers for WZ attack regions, including horizontal mirroring. */
public final class BossActionGeometry {
    private BossActionGeometry() {
    }

    public static boolean contains(BossAction.OrdinaryAttack attack, Point origin,
                                   Point target, boolean facingLeft) {
        return contains(attack, origin, target, facingLeft, List.of());
    }

    public static boolean contains(BossAction.OrdinaryAttack attack, Point origin,
                                   Point target, boolean facingLeft,
                                   List<Integer> selectedRegions) {
        Point lt = attack.lt();
        Point rb = attack.rb();
        if (origin == null || target == null || lt == null || rb == null) {
            return false;
        }
        if (attack.hasDistributedRegions()) {
            return selectedRegions.stream().anyMatch(region -> containsRegion(
                    attack, origin, target, facingLeft, region));
        }
        int minX = facingLeft ? origin.x + lt.x : origin.x - rb.x;
        int maxX = facingLeft ? origin.x + rb.x : origin.x - lt.x;
        return target.x >= minX && target.x <= maxX
                && target.y >= origin.y + lt.y && target.y <= origin.y + rb.y;
    }

    private static boolean containsRegion(BossAction.OrdinaryAttack attack, Point origin,
                                          Point target, boolean facingLeft, int region) {
        if (region < 0 || region >= attack.areaCount()) {
            return false;
        }
        int offset = (attack.areaStart() + region) * 100;
        if (!facingLeft) {
            offset = -offset;
        }
        int minX = origin.x + offset + attack.lt().x;
        int maxX = origin.x + offset + attack.rb().x;
        return target.x >= Math.min(minX, maxX) && target.x <= Math.max(minX, maxX)
                && target.y >= origin.y + attack.lt().y
                && target.y <= origin.y + attack.rb().y;
    }
}
