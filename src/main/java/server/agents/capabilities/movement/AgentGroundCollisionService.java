package server.agents.capabilities.movement;

import server.agents.physics.AgentWallCollisionPolicy;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationWalkRegionLookupService;
import server.maps.Foothold;
import server.maps.MapleMap;

import java.awt.Point;
import java.awt.Rectangle;

/**
 * Agent-owned seam for grounded collision and ledge queries.
 */
public final class AgentGroundCollisionService {
    private static final int REGION_STITCH_GAP_PX = config.AgentTuning.intValue("server.agents.capabilities.movement.AgentGroundCollisionService.REGION_STITCH_GAP_PX");
    private static final int SYNTHETIC_MAP_BOUND_SIZE = 1 << 18;

    private AgentGroundCollisionService() {
    }

    public static boolean canWalkGroundStep(MapleMap map, Point from, int stepX) {
        if (map == null || from == null) {
            return false;
        }
        Foothold foothold = AgentGroundingService.findGroundFoothold(map, from);
        return canWalkGroundStep(map, from, foothold, stepX);
    }

    public static boolean canWalkGroundStep(MapleMap map, Point from, Foothold foothold, int stepX) {
        if (map == null || from == null) {
            return false;
        }
        GroundStepPreview preview = previewGroundStep(map, from, foothold, from.x + stepX);
        return preview != null && !preview.lostGround() && !preview.blocked();
    }

    public static boolean isGroundStepBlockedByWall(MapleMap map, Point from, int stepX) {
        if (map == null || from == null || stepX == 0) {
            return false;
        }
        Foothold foothold = AgentGroundingService.findGroundFoothold(map, from);
        return isGroundStepBlockedByWall(map, from, foothold, stepX);
    }

    public static boolean isGroundStepBlockedByWall(MapleMap map, Point from, Foothold foothold, int stepX) {
        if (map == null || from == null || stepX == 0) {
            return false;
        }
        GroundStepPreview preview = previewGroundStep(map, from, foothold, from.x + stepX);
        return preview != null && preview.blocked();
    }

    public static boolean isGroundRunwayBlockedByWall(MapleMap map, Point from, Point to) {
        GroundCollision collision = findGroundWallCollision(map, from, to);
        if (collision.type() != CollisionType.WALL) {
            return false;
        }
        Foothold standing = AgentGroundingService.findGroundFoothold(map, from);
        return collision.foothold() == null || !hasWalkRegion(map, standing);
    }

    public static boolean canStartDownJump(MapleMap map, Point position) {
        Foothold foothold = AgentGroundingService.findGroundFoothold(map, position);
        return foothold != null && !foothold.isForbidFallDown();
    }

    public static boolean isGroundFarBelow(MapleMap map, Point position) {
        if (map == null || position == null) {
            return true;
        }
        Point ground = AgentGroundingService.findGroundPoint(map, position);
        return ground == null || ground.y > position.y + AgentMovementPhysicsConfig.configuredMaxSnapDrop();
    }

    public static Point findWalkRegionGroundPoint(MapleMap map, Foothold foothold, int x, int referenceY) {
        GroundRegionSample sample = findWalkRegionGroundSample(map, foothold, x, referenceY);
        return sample == null ? null : sample.point();
    }

    public static Foothold findWalkRegionGroundFoothold(
            MapleMap map,
            int regionId,
            int x,
            int referenceY) {
        AgentNavigationWalkRegionLookupService.WalkRegionLookup lookup =
                AgentNavigationWalkRegionLookupService.resolveWalkRegionLookup(map);
        GroundRegionSample sample = findWalkRegionGroundSample(
                lookup, regionId, null, x, referenceY);
        return sample == null ? null : sample.foothold();
    }

    static GroundStepPreview previewGroundStep(MapleMap map, Point currentPos, Foothold foothold, int nextX) {
        if (map == null || currentPos == null) {
            return null;
        }

        boolean constrainToWalkRegion = hasWalkRegion(map, foothold);
        Point standingPoint = constrainToWalkRegion
                ? findWalkRegionGroundPoint(map, foothold, currentPos.x, currentPos.y)
                : null;
        if (standingPoint == null) {
            standingPoint = AgentGroundingService.findGroundPoint(map, currentPos);
        }

        int baseY = standingPoint != null
                && Math.abs(standingPoint.y - currentPos.y) <= AgentMovementPhysicsConfig.configuredMaxSlopeUp()
                ? standingPoint.y
                : currentPos.y;

        GroundCollision wall = constrainToWalkRegion
                ? mapSideBoundaryCollision(map, currentPos, new Point(nextX, baseY))
                : findGroundWallCollision(map, currentPos, new Point(nextX, baseY));
        if (wall.type() == CollisionType.WALL) {
            return new GroundStepPreview(baseY, currentPos, foothold, false, true);
        }

        Point snappedPoint;
        Foothold snappedFoothold;
        boolean lostGround;
        if (constrainToWalkRegion) {
            GroundRegionSample snappedSample = findWalkRegionGroundSample(map, foothold, nextX, baseY);
            snappedPoint = snappedSample == null ? null : snappedSample.point();
            snappedFoothold = snappedSample == null ? null : snappedSample.foothold();
            lostGround = snappedPoint == null
                    || snappedPoint.y > baseY + AgentMovementPhysicsConfig.configuredMaxSnapDrop();
        } else {
            int probeY = Math.max(currentPos.y, baseY + 1);
            snappedPoint = AgentGroundingService.findGroundPoint(map, new Point(nextX, probeY));
            lostGround = snappedPoint == null
                    || snappedPoint.y > baseY + AgentMovementPhysicsConfig.configuredMaxSnapDrop();
            snappedFoothold = snappedPoint == null || map.getFootholds() == null
                    ? null
                    : map.getFootholds().findBelow(new Point(nextX, snappedPoint.y + 1));
        }

        return new GroundStepPreview(baseY, snappedPoint, snappedFoothold, lostGround, false);
    }

    private static boolean hasWalkRegion(MapleMap map, Foothold foothold) {
        if (map == null || foothold == null) {
            return false;
        }
        AgentNavigationWalkRegionLookupService.WalkRegionLookup lookup =
                AgentNavigationWalkRegionLookupService.resolveWalkRegionLookup(map);
        if (lookup == null) {
            return false;
        }
        return lookup.regionIdByFootholdId().getOrDefault(foothold.getId(), -1) >= 0;
    }

    private static boolean isChainStep(Foothold foothold, int candidateFootholdId) {
        return candidateFootholdId == foothold.getId()
                || candidateFootholdId == foothold.getNext()
                || candidateFootholdId == foothold.getPrev();
    }

    private static GroundRegionSample findWalkRegionGroundSample(MapleMap map, Foothold foothold, int x, int referenceY) {
        if (map == null || foothold == null) {
            return null;
        }

        AgentNavigationWalkRegionLookupService.WalkRegionLookup lookup =
                AgentNavigationWalkRegionLookupService.resolveWalkRegionLookup(map);
        if (lookup == null) {
            return null;
        }
        int regionId = lookup.regionIdByFootholdId().getOrDefault(foothold.getId(), -1);
        return findWalkRegionGroundSample(lookup, regionId, foothold, x, referenceY);
    }

    private static GroundRegionSample findWalkRegionGroundSample(
            AgentNavigationWalkRegionLookupService.WalkRegionLookup lookup,
            int regionId,
            Foothold foothold,
            int x,
            int referenceY) {
        if (lookup == null) {
            return null;
        }
        AgentNavigationGraph.Region region = lookup.regionsById().get(regionId);
        if (region == null || region.isRopeRegion) {
            return null;
        }

        AgentNavigationGraph.Segment bestSegment = null;
        Point bestPoint = null;
        int bestScore = Integer.MAX_VALUE;
        boolean bestChainStep = false;
        boolean foundContainingSegment = false;
        for (AgentNavigationGraph.Segment segment : region.segments) {
            if (segment.containsX(x)) {
                foundContainingSegment = true;
                break;
            }
        }

        for (AgentNavigationGraph.Segment segment : region.segments) {
            int dx = distanceToSegmentX(segment, x);
            boolean containsX = segment.containsX(x);
            if (!containsX && (foundContainingSegment || dx > REGION_STITCH_GAP_PX)) {
                continue;
            }

            Point candidate = segment.pointAt(x);
            int dy = candidate.y - referenceY;
            if (dy > AgentMovementPhysicsConfig.configuredMaxSnapDrop()
                    || dy < -AgentMovementPhysicsConfig.configuredMaxSlopeUp()) {
                continue;
            }

            boolean chainStep = foothold == null || isChainStep(foothold, segment.footholdId);
            int score = dx * 1000 + Math.abs(dy);
            boolean better = bestSegment == null
                    || (chainStep && !bestChainStep)
                    || (chainStep == bestChainStep
                    && (score < bestScore || (score == bestScore && candidate.y > bestPoint.y)));
            if (better) {
                bestSegment = segment;
                bestPoint = candidate;
                bestScore = score;
                bestChainStep = chainStep;
            }
        }

        if (bestSegment == null) {
            return null;
        }

        Foothold bestFoothold = lookup.footholdsById().get(bestSegment.footholdId);
        if (bestFoothold == null) {
            return null;
        }
        return new GroundRegionSample(bestPoint, bestFoothold);
    }

    private static int distanceToSegmentX(AgentNavigationGraph.Segment segment, int x) {
        if (segment.containsX(x)) {
            return 0;
        }
        return x < segment.minX ? segment.minX - x : x - segment.maxX;
    }

    private static GroundCollision findGroundWallCollision(MapleMap map, Point previousPos, Point nextPos) {
        if (map == null || map.getFootholds() == null || previousPos == null || nextPos == null) {
            return GroundCollision.none();
        }
        if (previousPos.x == nextPos.x) {
            return GroundCollision.none();
        }

        GroundCollision best = mapSideBoundaryCollision(map, previousPos, nextPos);
        for (Foothold foothold : map.getFootholds().getAllFootholds()) {
            if (!AgentWallCollisionPolicy.collides(map, foothold, AgentWallCollisionPolicy.ALL_GROUPS)) {
                continue;
            }
            GroundCollision collision = wallCollision(foothold, previousPos, nextPos);
            if (collision.type() == CollisionType.WALL && collision.progress() < best.progress()) {
                best = collision;
            }
        }
        return best;
    }

    private static GroundCollision mapSideBoundaryCollision(MapleMap map, Point previousPos, Point nextPos) {
        Rectangle area = map.getMapArea();
        if (area == null || area.width <= 0 || area.height <= 0 || previousPos.x == nextPos.x) {
            return GroundCollision.none();
        }

        int dir = Integer.compare(nextPos.x, previousPos.x);
        int boundaryX = dir > 0 ? effectiveRightBoundaryX(map, area) : effectiveLeftBoundaryX(map, area);
        if (dir > 0 && (previousPos.x > boundaryX || nextPos.x <= boundaryX)) {
            return GroundCollision.none();
        }
        if (dir < 0 && (previousPos.x < boundaryX || nextPos.x >= boundaryX)) {
            return GroundCollision.none();
        }

        double progress = (boundaryX - previousPos.x) / (double) (nextPos.x - previousPos.x);
        if (progress < 0.0 || progress > 1.0) {
            return GroundCollision.none();
        }

        double yAtBoundary = previousPos.y + (nextPos.y - previousPos.y) * progress;
        return new GroundCollision(CollisionType.WALL, progress, null);
    }

    private static int effectiveLeftBoundaryX(MapleMap map, Rectangle area) {
        if (!hasUsableFootholdXBounds(map)) {
            return area.x;
        }
        int footholdMinX = map.getFootholds().getMinDropX();
        if (isSyntheticMapArea(area)) {
            return footholdMinX;
        }
        return Math.min(area.x, footholdMinX);
    }

    private static int effectiveRightBoundaryX(MapleMap map, Rectangle area) {
        if (!hasUsableFootholdXBounds(map)) {
            return area.x + area.width;
        }
        int footholdMaxX = map.getFootholds().getMaxDropX();
        if (isSyntheticMapArea(area)) {
            return footholdMaxX;
        }
        return Math.max(area.x + area.width, footholdMaxX);
    }

    private static boolean isSyntheticMapArea(Rectangle area) {
        return area.width >= SYNTHETIC_MAP_BOUND_SIZE && area.height >= SYNTHETIC_MAP_BOUND_SIZE;
    }

    private static boolean hasUsableFootholdXBounds(MapleMap map) {
        return map.getFootholds() != null
                && map.getFootholds().getMinDropX() < map.getFootholds().getMaxDropX();
    }

    private static GroundCollision wallCollision(Foothold wall, Point previousPos, Point nextPos) {
        int wallX = wall.getX1();
        int startX = previousPos.x;
        int endX = nextPos.x;
        if (startX == endX) {
            return GroundCollision.none();
        }

        double progress = (wallX - startX) / (double) (endX - startX);
        if (progress <= 0.0 || progress > 1.0) {
            return GroundCollision.none();
        }

        double yAtWall = previousPos.y + (nextPos.y - previousPos.y) * progress;
        int minY = Math.min(wall.getY1(), wall.getY2());
        int maxY = Math.max(wall.getY1(), wall.getY2());
        if (yAtWall < minY || yAtWall > maxY) {
            return GroundCollision.none();
        }
        if (isWalkableGroundWallEndpoint(yAtWall, minY, maxY)) {
            return GroundCollision.none();
        }

        return new GroundCollision(CollisionType.WALL, progress, wall);
    }

    private static boolean isWalkableGroundWallEndpoint(double yAtWall, int minY, int maxY) {
        if (Math.abs(yAtWall - minY) < 0.001) {
            return true;
        }
        return Math.abs(yAtWall - maxY) < 0.001
                && maxY - minY <= AgentMovementPhysicsConfig.configuredMaxSlopeUp();
    }

    private enum CollisionType {
        NONE,
        WALL
    }

    private record GroundCollision(CollisionType type, double progress, Foothold foothold) {
        static GroundCollision none() {
            return new GroundCollision(CollisionType.NONE, Double.POSITIVE_INFINITY, null);
        }
    }

    private record GroundRegionSample(Point point, Foothold foothold) {
    }

    record GroundStepPreview(int baseY, Point point, Foothold foothold, boolean lostGround, boolean blocked) {
    }
}
