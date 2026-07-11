package server.agents.capabilities.movement;

import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.navigation.AgentNavigationPhysicsService;
import server.maps.Foothold;
import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Set;

public final class AgentJumpProbeService {
    private static final int SYNTHETIC_MAP_BOUND_SIZE = 1 << 18;
    private static final float MAX_FALL_PXS = 670.0f;

    private AgentJumpProbeService() {
    }

    public static AgentJumpLanding simulateJumpLanding(MapleMap map, Point from, int stepX) {
        return simulateJumpLanding(map, from, stepX, AgentMovementProfile.base());
    }

    public static AgentJumpLanding simulateJumpLanding(MapleMap map,
                                                       Point from,
                                                       int stepX,
                                                       AgentMovementProfile profile) {
        return simulateLanding(map, from, -AgentMovementKinematicsService.jumpForcePerTick(profile), stepX, 0L);
    }

    public static AgentJumpLanding simulateRopeJumpLanding(MapleMap map,
                                                           Point from,
                                                           int stepX,
                                                           AgentMovementProfile profile) {
        return simulateLanding(map, from, -AgentMovementKinematicsService.ropeJumpForcePerTick(profile), stepX, 0L);
    }

    public static AgentJumpLanding simulateFallLanding(MapleMap map, Point from, int stepX) {
        return simulateLanding(map, from, 0f, stepX, 0L);
    }

    public static AgentJumpLanding simulateDownJumpLanding(MapleMap map, Point from) {
        if (!AgentGroundCollisionService.canStartDownJump(map, from)) {
            return null;
        }
        return simulateLanding(map, from, -AgentAirborneLaunchService.downJumpForcePerTick(), 0,
                AgentMovementPhysicsConfig.configuredDownJumpGraceMs());
    }

    public static AgentWalkOffLanding simulateWalkOffLanding(MapleMap map,
                                                             Point from,
                                                             int desiredDir,
                                                             AgentMovementProfile profile) {
        return from == null ? null : simulateWalkOffLanding(
                map, from, desiredDir, new AgentGroundTravelState(from.x, 0.0, 0.0), profile);
    }

    public static AgentWalkOffLanding simulateWalkOffLanding(MapleMap map,
                                                             Point from,
                                                             int desiredDir,
                                                             AgentGroundTravelState initialState,
                                                             AgentMovementProfile profile) {
        if (map == null || from == null || desiredDir == 0 || initialState == null) {
            return null;
        }

        Foothold foothold = AgentGroundingService.findGroundFoothold(map, from);
        if (foothold == null) {
            return null;
        }

        Point cursor = new Point(from);
        Foothold currentFoothold = foothold;
        AgentGroundTravelState state = initialState;
        int elapsedMs = 0;
        for (int i = 0; i < 256; i++) {
            AgentGroundPhysicsService.GroundStepResult step =
                    AgentGroundPhysicsService.simulateGroundMotion(map, cursor, currentFoothold, desiredDir, state, profile);
            if (step.lostGround()) {
                if (step.stepX() == 0) {
                    return null;
                }
                AgentJumpLanding landing = simulateFallLanding(map, step.point(), step.stepX());
                if (landing == null) {
                    return null;
                }
                return new AgentWalkOffLanding(new Point(step.point()), step.stepX(), landing,
                        elapsedMs + estimateFallLandingTimeMs(map, step.point(), step.stepX()));
            }

            cursor = step.point();
            currentFoothold = step.foothold() != null ? step.foothold() : currentFoothold;
            state = step.state();
            elapsedMs += AgentMovementPhysicsConfig.configuredMovementTickMs();
        }
        return null;
    }

    public static int estimateFallLandingTimeMs(MapleMap map, Point from, int stepX) {
        return estimateLandingTimeMs(map, from, 0f, stepX, 0L);
    }

    public static Point simulateGroundJumpRopeGrab(MapleMap map,
                                                   Point from,
                                                   int stepX,
                                                   Rope targetRope,
                                                   AgentMovementProfile profile) {
        return simulateRopeGrab(map, from, -AgentMovementKinematicsService.jumpForcePerTick(profile), stepX,
                targetRope, 0L);
    }

    public static int estimateGroundJumpRopeGrabTimeMs(MapleMap map,
                                                       Point from,
                                                       int stepX,
                                                       Rope targetRope,
                                                       AgentMovementProfile profile) {
        return estimateRopeGrabTimeMs(map, from, -AgentMovementKinematicsService.jumpForcePerTick(profile), stepX,
                targetRope, 0L);
    }

    public static Point simulateDownJumpRopeGrab(MapleMap map, Point from, Rope targetRope) {
        return simulateRopeGrab(map, from, -AgentAirborneLaunchService.downJumpForcePerTick(), 0,
                targetRope, AgentMovementPhysicsConfig.configuredDownJumpGraceMs());
    }

    public static int estimateDownJumpRopeGrabTimeMs(MapleMap map, Point from, Rope targetRope) {
        return estimateRopeGrabTimeMs(map, from, -AgentAirborneLaunchService.downJumpForcePerTick(), 0,
                targetRope, AgentMovementPhysicsConfig.configuredDownJumpGraceMs());
    }

    public static int estimateRopeJumpLandingTimeMs(MapleMap map,
                                                    Point from,
                                                    int stepX,
                                                    AgentMovementProfile profile) {
        return estimateLandingTimeMs(map, from, -AgentMovementKinematicsService.ropeJumpForcePerTick(profile),
                stepX, 0L);
    }

    public static Point simulateRopeJumpGrab(MapleMap map,
                                             Point from,
                                             int stepX,
                                             Rope targetRope,
                                             AgentMovementProfile profile) {
        return simulateRopeGrab(map, from, -AgentMovementKinematicsService.ropeJumpForcePerTick(profile), stepX,
                targetRope, 0L);
    }

    public static int estimateRopeJumpGrabTimeMs(MapleMap map,
                                                 Point from,
                                                 int stepX,
                                                 Rope targetRope,
                                                 AgentMovementProfile profile) {
        return estimateRopeGrabTimeMs(map, from, -AgentMovementKinematicsService.ropeJumpForcePerTick(profile), stepX,
                targetRope, 0L);
    }

    public static AgentPostLandingJump simulateJumpLandingWithPostLandingTicks(MapleMap map,
                                                                               Point from,
                                                                               int stepX,
                                                                               AgentMovementProfile profile,
                                                                               int postLandingTicks) {
        AgentJumpLanding landing = simulateJumpLanding(map, from, stepX, profile);
        if (landing == null) {
            return null;
        }
        return simulatePostLandingGroundTicks(map, landing, Integer.compare(stepX, 0), profile, postLandingTicks);
    }

    public static boolean canReachRopeFromGround(MapleMap map, Point from, Rope rope) {
        return canReachRopeFromGround(map, from, rope, AgentMovementProfile.base());
    }

    public static boolean canReachRopeFromGround(MapleMap map, Point from, Rope rope, AgentMovementProfile profile) {
        int dx = Math.abs(rope.x() - from.x);
        if (dx <= AgentMovementPhysicsConfig.configuredRopeGrabX()
                && from.y >= AgentNavigationPhysicsService.firstClimbableY(rope)
                && from.y <= rope.bottomY()) {
            return true;
        }
        if (rope.topY() >= from.y) {
            return false;
        }

        int jumpReach = (int) Math.ceil(AgentMovementKinematicsService.calculateMaxJumpHeight(profile));
        return rope.bottomY() >= from.y - jumpReach
                && dx <= AgentMovementKinematicsService.maxJumpHorizontalTravel(map, profile);
    }

    private static AgentPostLandingJump simulatePostLandingGroundTicks(MapleMap map,
                                                                       AgentJumpLanding landing,
                                                                       int desiredDir,
                                                                       AgentMovementProfile profile,
                                                                       int ticks) {
        Point cursor = landing.point();
        Foothold currentFoothold = landing.foothold();
        AgentGroundTravelState state = new AgentGroundTravelState(cursor.x, 0.0, 0.0);
        for (int i = 0; i < Math.max(0, ticks); i++) {
            AgentGroundPhysicsService.GroundStepResult step =
                    AgentGroundPhysicsService.simulateGroundMotion(map, cursor, currentFoothold, desiredDir, state, profile);
            if (step.lostGround()) {
                return new AgentPostLandingJump(landing, step.point(), currentFoothold, true);
            }
            cursor = step.point();
            currentFoothold = step.foothold() != null ? step.foothold() : currentFoothold;
            state = step.state();
        }
        return new AgentPostLandingJump(landing, cursor, currentFoothold, false);
    }

    private static Point simulateRopeGrab(MapleMap map,
                                          Point from,
                                          float initialVelocityY,
                                          int stepX,
                                          Rope targetRope,
                                          long landingGraceMs) {
        RopeGrabResult result = simulateRopeGrabCore(map, from, initialVelocityY, stepX, targetRope, landingGraceMs);
        return result != null ? result.point() : null;
    }

    private static int estimateRopeGrabTimeMs(MapleMap map,
                                              Point from,
                                              float initialVelocityY,
                                              int stepX,
                                              Rope targetRope,
                                              long landingGraceMs) {
        RopeGrabResult result = simulateRopeGrabCore(map, from, initialVelocityY, stepX, targetRope, landingGraceMs);
        return result != null ? result.ticks() * AgentMovementPhysicsConfig.configuredMovementTickMs()
                : Integer.MAX_VALUE;
    }

    private static RopeGrabResult simulateRopeGrabCore(MapleMap map,
                                                       Point from,
                                                       float initialVelocityY,
                                                       int stepX,
                                                       Rope targetRope,
                                                       long landingGraceMs) {
        if (targetRope == null) {
            return null;
        }

        float velocityY = initialVelocityY;
        double physicsX = from.x;
        double physicsY = from.y;
        int previousIntY = from.y;
        long remainingLandingGraceMs = Math.max(0L, landingGraceMs);
        float gravity = AgentMovementKinematicsService.gravityPerTick();
        float maxFall = maxFallPerTick();

        for (int tick = 0; tick < (1500 / AgentMovementPhysicsConfig.configuredMovementTickMs()); tick++) {
            Point current = new Point((int) Math.round(physicsX), (int) Math.round(physicsY));
            if (canGrabRopeAtPoint(current, targetRope)) {
                return new RopeGrabResult(new Point(targetRope.x(), current.y), tick);
            }

            if (remainingLandingGraceMs > 0L) {
                remainingLandingGraceMs = Math.max(0L,
                        remainingLandingGraceMs - AgentMovementPhysicsConfig.configuredMovementTickMs());
            }

            physicsX += stepX;
            physicsY += velocityY + 0.5f * gravity;
            velocityY = Math.min(velocityY + gravity, maxFall);

            int x = (int) Math.round(physicsX);
            int intY = (int) Math.round(physicsY);
            AirCollision collision = resolveAirCollision(map, new Point((int) Math.round(physicsX - stepX), previousIntY),
                    new Point(x, intY));
            if (collision.type() == AirCollisionType.WALL) {
                physicsX = collision.point().x;
                physicsY = collision.point().y;
                stepX = 0;
                previousIntY = collision.point().y;
                continue;
            }
            if (collision.type() == AirCollisionType.CEILING) {
                physicsX = collision.point().x;
                physicsY = collision.point().y;
                velocityY = 0f;
                previousIntY = collision.point().y;
                continue;
            }
            if (collision.type() == AirCollisionType.LAND && remainingLandingGraceMs == 0L) {
                return null;
            }

            previousIntY = intY;
        }

        return null;
    }

    private static boolean canGrabRopeAtPoint(Point position, Rope rope) {
        return Math.abs(position.x - rope.x()) <= AgentMovementPhysicsConfig.configuredRopeGrabX()
                && position.y >= AgentNavigationPhysicsService.firstClimbableY(rope)
                && position.y <= rope.bottomY();
    }

    private static AgentJumpLanding simulateLanding(MapleMap map,
                                                    Point from,
                                                    float initialVelocityY,
                                                    int stepX,
                                                    long landingGraceMs) {
        float velocityY = initialVelocityY;
        double physicsX = from.x;
        double physicsY = from.y;
        int previousIntY = from.y;
        long remainingLandingGraceMs = Math.max(0L, landingGraceMs);
        float gravity = AgentMovementKinematicsService.gravityPerTick();
        float maxFall = maxFallPerTick();

        for (int tick = 0; tick < (1500 / AgentMovementPhysicsConfig.configuredMovementTickMs()); tick++) {
            if (remainingLandingGraceMs > 0L) {
                remainingLandingGraceMs = Math.max(0L,
                        remainingLandingGraceMs - AgentMovementPhysicsConfig.configuredMovementTickMs());
            }

            physicsX += stepX;
            physicsY += velocityY + 0.5f * gravity;
            velocityY = Math.min(velocityY + gravity, maxFall);

            int x = (int) Math.round(physicsX);
            int intY = (int) Math.round(physicsY);
            Point previousPoint = new Point((int) Math.round(physicsX - stepX), previousIntY);
            Point nextPoint = new Point(x, intY);
            AirCollision collision = resolveAirCollision(map, previousPoint, nextPoint);
            if (collision.type() == AirCollisionType.WALL) {
                physicsX = collision.point().x;
                physicsY = collision.point().y;
                stepX = 0;
                previousIntY = collision.point().y;
                continue;
            }
            if (collision.type() == AirCollisionType.CEILING) {
                physicsX = collision.point().x;
                physicsY = collision.point().y;
                velocityY = 0f;
                previousIntY = collision.point().y;
                continue;
            }
            if (collision.type() == AirCollisionType.LAND && remainingLandingGraceMs == 0L) {
                return new AgentJumpLanding(collision.point(), collision.foothold(),
                        nextPoint.x - previousPoint.x, nextPoint.y - previousPoint.y,
                        (tick + 1) * AgentMovementPhysicsConfig.configuredMovementTickMs());
            }

            previousIntY = intY;
        }

        return null;
    }

    private static int estimateLandingTimeMs(MapleMap map,
                                             Point from,
                                             float initialVelocityY,
                                             int stepX,
                                             long landingGraceMs) {
        AgentJumpLanding landing = simulateLanding(map, from, initialVelocityY, stepX, landingGraceMs);
        return landing != null ? landing.timeMs() : Integer.MAX_VALUE;
    }

    static AirCollision resolveAirCollision(MapleMap map, Point previousPos, Point nextPos) {
        return resolveAirCollision(map, previousPos, nextPos,
                AgentWallCollisionPolicy.moverZMassAt(map, previousPos));
    }

    static AirCollision resolveAirCollision(MapleMap map, Point previousPos, Point nextPos, int moverZMass) {
        if (map == null || map.getFootholds() == null || previousPos == null || nextPos == null) {
            return AirCollision.none();
        }
        AirCollision wall = findWallCollision(map, previousPos, nextPos, moverZMass);
        AirCollision ceiling = findCeilingCollision(map, previousPos, nextPos);
        AirCollision landing = findGroundCollision(map, previousPos, nextPos);
        AirCollision best = AirCollision.none();
        if (wall.type() != AirCollisionType.NONE) {
            best = wall;
        }
        if (ceiling.type() != AirCollisionType.NONE && ceiling.progress() < best.progress()) {
            best = ceiling;
        }
        if (landing.type() != AirCollisionType.NONE && landing.progress() < best.progress()) {
            best = landing;
        }
        return best;
    }

    private static AirCollision findWallCollision(MapleMap map, Point previousPos, Point nextPos, int moverZMass) {
        if (map == null || map.getFootholds() == null || previousPos.x == nextPos.x) {
            return AirCollision.none();
        }

        AirCollision best = mapSideBoundaryCollision(map, previousPos, nextPos);
        for (Foothold foothold : map.getFootholds().getAllFootholds()) {
            if (!AgentWallCollisionPolicy.collides(map, foothold, moverZMass)) {
                continue;
            }
            AirCollision collision = wallCollision(foothold, previousPos, nextPos, false);
            if (collision.type() == AirCollisionType.WALL && collision.progress() < best.progress()) {
                best = collision;
            }
        }
        return best;
    }

    private static AirCollision mapSideBoundaryCollision(MapleMap map, Point previousPos, Point nextPos) {
        Rectangle area = map.getMapArea();
        if (area == null || area.width <= 0 || area.height <= 0 || previousPos.x == nextPos.x) {
            return AirCollision.none();
        }

        int dir = Integer.compare(nextPos.x, previousPos.x);
        int boundaryX = dir > 0 ? effectiveRightBoundaryX(map, area) : effectiveLeftBoundaryX(map, area);
        if (dir > 0 && (previousPos.x > boundaryX || nextPos.x <= boundaryX)) {
            return AirCollision.none();
        }
        if (dir < 0 && (previousPos.x < boundaryX || nextPos.x >= boundaryX)) {
            return AirCollision.none();
        }

        double progress = (boundaryX - previousPos.x) / (double) (nextPos.x - previousPos.x);
        if (progress < 0.0 || progress > 1.0) {
            return AirCollision.none();
        }

        double yAtBoundary = previousPos.y + (nextPos.y - previousPos.y) * progress;
        return new AirCollision(AirCollisionType.WALL,
                new Point(boundaryX, (int) Math.round(yAtBoundary)),
                null,
                progress);
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

    private static Set<Integer> getCollidableFromBelowIds(MapleMap map) {
        Set<Integer> cached = AgentNavigationGraphService.getCachedCollidableFromBelowIds(map.getId());
        if (cached != null) {
            return cached;
        }
        return AgentNavigationGraphService.computeCollidableFromBelowIds(map);
    }

    private static AirCollision findCeilingCollision(MapleMap map, Point previousPos, Point nextPos) {
        if (map == null || map.getFootholds() == null || nextPos.y >= previousPos.y) {
            return AirCollision.none();
        }

        Set<Integer> collidableFromBelow = getCollidableFromBelowIds(map);
        if (collidableFromBelow.isEmpty()) {
            return AirCollision.none();
        }

        AirCollision best = AirCollision.none();
        for (Foothold foothold : map.getFootholds().getAllFootholds()) {
            if (foothold.isWall() || !collidableFromBelow.contains(foothold.getId())) {
                continue;
            }
            AirCollision collision = ceilingCollision(foothold, previousPos, nextPos);
            if (collision.type() == AirCollisionType.CEILING && collision.progress() < best.progress()) {
                best = collision;
            }
        }
        return best;
    }

    private static AirCollision findGroundCollision(MapleMap map, Point previousPos, Point nextPos) {
        if (map == null || previousPos == null || nextPos == null || nextPos.y < previousPos.y) {
            return AirCollision.none();
        }

        int startX = previousPos.x;
        int endX = nextPos.x;
        int dir = Integer.compare(endX, startX);
        if (dir == 0) {
            return landingAtX(map, previousPos, nextPos, startX, 1.0);
        }

        int steps = Math.abs(endX - startX);
        for (int i = 0; i <= steps; i++) {
            int x = startX + dir * i;
            double progress = i / (double) steps;
            AirCollision landing = landingAtX(map, previousPos, nextPos, x, progress);
            if (landing.type == AirCollisionType.LAND) {
                return landing;
            }
        }
        return AirCollision.none();
    }

    private static AirCollision landingAtX(MapleMap map,
                                           Point previousPos,
                                           Point nextPos,
                                           int x,
                                           double progress) {
        int yAtX = (int) Math.round(previousPos.y + (nextPos.y - previousPos.y) * progress);
        AirCollision landing = landingAtProbeY(map, previousPos, x, yAtX, progress, previousPos.y + 1, false);
        if (landing.type() == AirCollisionType.LAND) {
            return landing;
        }

        if (x != previousPos.x) {
            return landingAtProbeY(map, previousPos, x, yAtX, progress, previousPos.y, true);
        }

        return AirCollision.none();
    }

    private static AirCollision landingAtProbeY(MapleMap map,
                                                Point previousPos,
                                                int x,
                                                int yAtX,
                                                double progress,
                                                int probeY,
                                                boolean requireTangentFloor) {
        Point probe = new Point(x, probeY);
        Point floor = map.getPointBelow(probe);
        if (floor == null) {
            return AirCollision.none();
        }

        int minY = Math.min(previousPos.y, yAtX);
        int maxY = Math.max(previousPos.y, yAtX);
        if (floor.y < minY || floor.y > maxY) {
            return AirCollision.none();
        }
        if (requireTangentFloor && floor.y != previousPos.y) {
            return AirCollision.none();
        }

        Foothold foothold = map.getFootholds().findBelow(probe);
        if (foothold == null) {
            return AirCollision.none();
        }

        return new AirCollision(AirCollisionType.LAND, new Point(x, floor.y), foothold, progress);
    }

    private static AirCollision wallCollision(Foothold wall,
                                              Point previousPos,
                                              Point nextPos,
                                              boolean allowWalkableGroundEndpoint) {
        int wallX = wall.getX1();
        int startX = previousPos.x;
        int endX = nextPos.x;
        if (startX == endX) {
            return AirCollision.none();
        }

        double progress = (wallX - startX) / (double) (endX - startX);
        if (progress <= 0.0 || progress > 1.0) {
            return AirCollision.none();
        }

        double yAtWall = previousPos.y + (nextPos.y - previousPos.y) * progress;
        int minY = Math.min(wall.getY1(), wall.getY2());
        int maxY = Math.max(wall.getY1(), wall.getY2());
        if (yAtWall < minY || yAtWall > maxY) {
            return AirCollision.none();
        }
        if (allowWalkableGroundEndpoint && isWalkableGroundWallEndpoint(yAtWall, minY, maxY)) {
            return AirCollision.none();
        }

        int dir = Integer.compare(endX, startX);
        int safeX = wallX - dir;
        return new AirCollision(AirCollisionType.WALL,
                new Point(safeX, (int) Math.round(yAtWall)),
                wall,
                progress);
    }

    private static AirCollision ceilingCollision(Foothold foothold, Point previousPos, Point nextPos) {
        if (foothold.getY1() != foothold.getY2()) {
            return AirCollision.none();
        }

        int ceilingY = foothold.getY1();
        if (ceilingY > previousPos.y || ceilingY < nextPos.y) {
            return AirCollision.none();
        }

        double progress = (ceilingY - previousPos.y) / (double) (nextPos.y - previousPos.y);
        if (progress <= 0.0 || progress > 1.0) {
            return AirCollision.none();
        }

        double xAtCeiling = previousPos.x + (nextPos.x - previousPos.x) * progress;
        int minX = Math.min(foothold.getX1(), foothold.getX2());
        int maxX = Math.max(foothold.getX1(), foothold.getX2());
        if (xAtCeiling < minX || xAtCeiling > maxX) {
            return AirCollision.none();
        }

        return new AirCollision(AirCollisionType.CEILING,
                new Point((int) Math.round(xAtCeiling), ceilingY + 1),
                foothold,
                progress);
    }

    private static boolean isWalkableGroundWallEndpoint(double yAtWall, int minY, int maxY) {
        if (Math.abs(yAtWall - minY) < 0.001) {
            return true;
        }
        return Math.abs(yAtWall - maxY) < 0.001
                && maxY - minY <= AgentMovementPhysicsConfig.configuredMaxSlopeUp();
    }

    private static float maxFallPerTick() {
        return MAX_FALL_PXS * AgentMovementPhysicsConfig.configuredMovementTickMs() / 1000f;
    }

    enum AirCollisionType {
        NONE,
        WALL,
        CEILING,
        LAND
    }

    record AirCollision(AirCollisionType type, Point point, Foothold foothold, double progress) {
        static AirCollision none() {
            return new AirCollision(AirCollisionType.NONE, null, null, Double.POSITIVE_INFINITY);
        }
    }

    private record RopeGrabResult(Point point, int ticks) {
    }
}
