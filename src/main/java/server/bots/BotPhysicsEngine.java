package server.bots;

import client.Character;
import constants.game.CharacterStance;
import server.maps.Foothold;
import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class BotPhysicsEngine {
    private static final double CLIENT_GROUND_STEP_MS = 8.0;
    private static final double CLIENT_GROUND_STEP_S = CLIENT_GROUND_STEP_MS / 1000.0;
    private static final int REGION_STITCH_GAP_PX = 2;
    // Max horizontal gap between adjacent foothold endpoints that the bot can walk across.
    // Shared with BotNavigationGraphProvider so walk-edge generation and physics agree.
    static final int WALK_GAP_PX = 12;

    static class Config {
        public int TICK_MS = 50;

        // Values below calibrated against real-client CP_USER_MOVE packet captures
        // (monitored-packets logs for speed=100/jump=100 walk, long-fall terminal
        // velocity, and rope climb/jump). Old values kept in comments for reference.
        public int WALK_VEL = 125;                  // was 150 (real client walkSpeed = 125 px/s)

        public float GRAVITY_PXS2 = 2000.0f;        // was 2187.5f (measured: exactly 2000 px/s^2)
        public float JUMP_SPEED_PXS = 555.0f;       // was 562.5f  (measured: -555 px/s jump kick)
        public float JUMP_DOWN_PXS = 196.0f;        // was 320 (measured: -196 px/s upward kick; old value caused re-land on origin platform)
        public float JUMP_ROPE_PXS = 375.0f;        // rope-jump finding (NOT applied): real client kick = (±162, -277) — fixed vx 162, vy 277
        public float MAX_FALL_PXS = 670.0f;         // confirmed exact (terminal velocity sustained in long-fall log)
        public double HFORCE_PXS = 16.667;          // was 20.0 (yields 125 px/s walk via hF*GROUNDSLIP/(FRICTION+SLOPEFACTOR))
        public double GROUNDSLIP = 3.0;
        public double FRICTION = 0.3;
        public double SLOPEFACTOR = 0.1;
        public double AIR_STEER_ACCEL = 0.5;   // px/tick added per tick toward target
        public double AIR_STEER_MAX   = 1.5;  // cap on air-steering speed (px/tick)

        public float CLIMB_SPEED_PXS = 100.0f;
        public int ROPE_GRAB_X = 8;
        public int MAX_SNAP_DROP = 16;
        public int MAX_SLOPE_UP = 26;
        public int DOWN_JUMP_GRACE_MS = 350;
    }

    record GroundMotion(int stepX, boolean lostGround) {
    }

    record GroundTravelState(double physX, double hspeed, double carryMs) {
    }

    record GroundStepResult(Point point,
                            Foothold foothold,
                            GroundTravelState state,
                            int stepX,
                            int velocityX,
                            boolean lostGround) {
    }

    private record GroundRegionSample(Point point, Foothold foothold) {
    }

    private record GroundStepPreview(int baseY, Point point, Foothold foothold, boolean lostGround, boolean blocked) {
    }

    private record WalkRegionLookup(int mapId,
                                    Map<Integer, BotNavigationGraph.Region> regionsById,
                                    Map<Integer, Integer> regionIdByFootholdId,
                                    Map<Integer, Foothold> footholdsById) {
    }

    record MovementSnapshot(int velX, int velY, int stance) {
    }

    static final class JumpLanding {
        private final Point point;
        private final Foothold foothold;
        private final double incomingDeltaX;
        private final double incomingDeltaY;
        private final int ticks;

        JumpLanding(Point point, Foothold foothold) {
            this(point, foothold, 0.0, 0.0, 0);
        }

        JumpLanding(Point point, Foothold foothold, double incomingDeltaX, double incomingDeltaY) {
            this(point, foothold, incomingDeltaX, incomingDeltaY, 0);
        }

        JumpLanding(Point point, Foothold foothold, double incomingDeltaX, double incomingDeltaY, int ticks) {
            this.point = point;
            this.foothold = foothold;
            this.incomingDeltaX = incomingDeltaX;
            this.incomingDeltaY = incomingDeltaY;
            this.ticks = ticks;
        }

        Point point() {
            return point;
        }

        Foothold foothold() {
            return foothold;
        }

        double incomingDeltaX() {
            return incomingDeltaX;
        }

        double incomingDeltaY() {
            return incomingDeltaY;
        }

        int timeMs() {
            return ticks * cfg.TICK_MS;
        }
    }

    record PostLandingJump(JumpLanding landing,
                           Point finalPoint,
                           Foothold finalFoothold,
                           boolean lostGround) {
    }

    record WalkOffLanding(Point launchPoint,
                          int launchStepX,
                          JumpLanding landing,
                          int travelTimeMs) {
    }

    private enum AirCollisionType {
        NONE,
        WALL,
        LAND
    }

    private record AirCollision(AirCollisionType type, Point point, Foothold foothold, double progress) {
        static AirCollision none() {
            return new AirCollision(AirCollisionType.NONE, null, null, Double.POSITIVE_INFINITY);
        }
    }

    private record RopeGrabResult(Point point, int ticks) {}

    enum AirborneStepResult {
        WALL,
        LANDED,
        CONTINUE
    }

    static Config cfg = new Config();
    private static final ThreadLocal<WalkRegionLookup> ACTIVE_BUILD_WALK_REGION_LOOKUP = new ThreadLocal<>();
    private static final Map<Integer, Map<Integer, Foothold>> FOOTHOLDS_BY_ID_BY_MAP_ID = new ConcurrentHashMap<>();

    private BotPhysicsEngine() {
    }

    private static BotMovementProfile profileOrBase(BotMovementProfile profile) {
        return profile != null ? profile : BotMovementProfile.base();
    }

    static float tickS() {
        return cfg.TICK_MS / 1000f;
    }

    static float maxFallPerTick() {
        return cfg.MAX_FALL_PXS * tickS();
    }

    static float jumpForcePerTick() {
        return cfg.JUMP_SPEED_PXS * tickS();
    }

    static float jumpForcePerTick(BotMovementProfile profile) {
        return profileOrBase(profile).jumpSpeedPxs() * tickS();
    }

    static float downJumpForcePerTick() {
        return cfg.JUMP_DOWN_PXS * tickS();
    }

    static float ropeJumpForcePerTick() {
        return cfg.JUMP_ROPE_PXS * tickS();
    }

    static float ropeJumpForcePerTick(BotMovementProfile profile) {
        return profileOrBase(profile).ropeJumpSpeedPxs() * tickS();
    }

    static int climbStepPerTick() {
        return Math.max(1, Math.round(cfg.CLIMB_SPEED_PXS * tickS()));
    }

    static float gravityPerTick() {
        float t = tickS();
        return cfg.GRAVITY_PXS2 * t * t;
    }

    static int walkStep(MapleMap map) {
        return walkStep(map, BotMovementProfile.base());
    }

    static int walkStep(MapleMap map, BotMovementProfile profile) {
        double step = maxHSpeedPerClientStep(profile) * cfg.TICK_MS * mapGroundSpeedScale(map) / CLIENT_GROUND_STEP_MS;
        return Math.max(1, (int) Math.round(step));
    }

    /**
     * Distance (px) needed for a bot starting from a standstill to reach (near) max walk speed
     * before walking off a ledge. O(1) derivation from config constants — used by graphgen to
     * place directional-drop launch points without simulating the runway tick by tick.
     *
     * <p>Friction model: dv/dt = hF*slip - (friction+slope)*v. Terminal v_max = hF*slip/(friction+slope).
     * Time to ~95% terminal ≈ 3/(friction+slope). We use a fixed multiple of walkStep that comfortably
     * exceeds the 95% mark for the calibrated constants without iterating.
     */
    static int launchRunwayPx(MapleMap map, BotMovementProfile profile) {
        int step = walkStep(map, profile);
        return Math.max(40, step * 6);
    }

    static int velocityFromDeltaX(double deltaX) {
        return (int) Math.round(deltaX * (1000.0 / cfg.TICK_MS));
    }

    static void syncGroundPosition(BotEntry entry, int x) {
        if (entry.hspeed == 0.0 && (int) Math.round(entry.physX) != x) {
            entry.physX = x;
        }
    }

    /**
     * Syncs ground position, then returns the foothold the bot is standing on.
     * If no foothold is found the bot has walked off the edge — physics starts a fall and this
     * returns null. Movement must check for null and return early without applying ground actions.
     */
    static Foothold syncAndDetectGround(BotEntry entry, Character bot) {
        syncGroundPosition(entry, bot.getPosition().x);
        Foothold fh = findGroundFoothold(bot.getMap(), bot.getPosition());
        if (fh == null) {
            beginFall(entry, bot, 0);
        }
        return fh;
    }

    static Foothold findGroundFoothold(MapleMap map, Point position) {
        if (map == null || map.getFootholds() == null || position == null) {
            return null;
        }

        Foothold exact = map.getFootholds().findBelow(position);
        Foothold offset = map.getFootholds().findBelow(new Point(position.x, position.y - cfg.MAX_SLOPE_UP));
        if (exact == null) return offset;
        if (offset == null) return exact;

        // On sloped footholds, integer truncation of the interpolated Y can make the foothold's
        // computed Y fall 1px above the player's stored position, causing findBelow to skip it
        // and return a distant platform instead. Mirror findGroundPoint: pick the closer result.
        Point exactGround = map.getPointBelow(position);
        Point offsetGround = map.getPointBelow(new Point(position.x, position.y - cfg.MAX_SLOPE_UP));
        if (exactGround == null) return offset;
        if (offsetGround == null) return exact;
        return Math.abs(offsetGround.y - position.y) < Math.abs(exactGround.y - position.y) ? offset : exact;
    }

    static Point findGroundPoint(MapleMap map, Point position) {
        if (map == null || position == null) {
            return null;
        }

        Point exactGround = map.getPointBelow(position);
        Point offsetGround = map.getPointBelow(new Point(position.x, position.y - cfg.MAX_SLOPE_UP));
        if (exactGround == null) {
            return offsetGround;
        }
        if (offsetGround == null) {
            return exactGround;
        }

        int exactDistance = Math.abs(exactGround.y - position.y);
        int offsetDistance = Math.abs(offsetGround.y - position.y);
        return offsetDistance < exactDistance ? offsetGround : exactGround;
    }

    // Canonical walk-connectivity rule shared by graph region merging and runtime ground traversal.
    // If two foothold endpoints form a walkable local step, they must be in the same walk region.
    static boolean canWalkAcrossFootholds(Foothold first, Foothold second) {
        if (first == null || second == null || first.isWall() || second.isWall()) {
            return false;
        }

        EndpointConnection connection = sharedEndpointConnection(first, second);
        if (connection == null) {
            connection = closestEndpointConnection(first, second);
            if (connection == null
                    || (Math.abs(connection.to().x - connection.from().x)
                    + Math.abs(connection.to().y - connection.from().y)) > 2) {
                return false;
            }
        }

        int dx = Math.abs(connection.to().x - connection.from().x);
        int dy = connection.to().y - connection.from().y;
        if (!isWalkableEndpointStep(dx, dy)) {
            return false;
        }

        return true;
    }

    private record EndpointConnection(Point from, Point to) {
    }

    private static Point[] endpoints(Foothold fh) {
        return new Point[]{new Point(fh.getX1(), fh.getY1()), new Point(fh.getX2(), fh.getY2())};
    }

    private static EndpointConnection closestEndpointConnection(Foothold first, Foothold second) {
        EndpointConnection best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Point from : endpoints(first)) {
            for (Point to : endpoints(second)) {
                int distance = Math.abs(to.x - from.x) + Math.abs(to.y - from.y);
                if (distance < bestDistance) {
                    best = new EndpointConnection(from, to);
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private static EndpointConnection sharedEndpointConnection(Foothold first, Foothold second) {
        for (Point from : endpoints(first)) {
            for (Point to : endpoints(second)) {
                if (from.equals(to)) {
                    return new EndpointConnection(from, to);
                }
            }
        }
        return null;
    }

    static Point findWalkRegionGroundPoint(MapleMap map, Foothold foothold, int x, int referenceY) {
        GroundRegionSample sample = findWalkRegionGroundSample(map, foothold, x, referenceY);
        return sample == null ? null : sample.point();
    }

    static boolean canWalkGroundStep(MapleMap map, Point currentPos, int stepX) {
        if (map == null || currentPos == null) {
            return false;
        }
        Foothold foothold = findGroundFoothold(map, currentPos);
        GroundStepPreview preview = previewGroundStep(map, currentPos, foothold, currentPos.x + stepX);
        return preview != null && !preview.lostGround() && !preview.blocked();
    }

    static boolean isGroundStepBlockedByWall(MapleMap map, Point currentPos, int stepX) {
        if (map == null || currentPos == null || stepX == 0) {
            return false;
        }
        Foothold foothold = findGroundFoothold(map, currentPos);
        GroundStepPreview preview = previewGroundStep(map, currentPos, foothold, currentPos.x + stepX);
        return preview != null && preview.blocked();
    }

    static boolean isGroundRunwayBlockedByWall(MapleMap map, Point from, Point to) {
        return findGroundWallCollision(map, from, to).type() == AirCollisionType.WALL;
    }

    static boolean isGroundFarBelow(MapleMap map, Point position) {
        if (map == null || position == null) {
            return true;
        }
        Point ground = findGroundPoint(map, position);
        return ground == null || ground.y > position.y + cfg.MAX_SNAP_DROP;
    }

    private static boolean hasWalkRegion(MapleMap map, Foothold foothold) {
        if (map == null || foothold == null) {
            return false;
        }
        WalkRegionLookup lookup = resolveWalkRegionLookup(map);
        if (lookup == null) {
            return false;
        }
        return lookup.regionIdByFootholdId().getOrDefault(foothold.getId(), -1) >= 0;
    }

    private static GroundRegionSample findWalkRegionGroundSample(MapleMap map, Foothold foothold, int x, int referenceY) {
        if (map == null || foothold == null) {
            return null;
        }

        WalkRegionLookup lookup = resolveWalkRegionLookup(map);
        if (lookup == null) {
            return null;
        }
        int regionId = lookup.regionIdByFootholdId().getOrDefault(foothold.getId(), -1);
        BotNavigationGraph.Region region = lookup.regionsById().get(regionId);
        if (region == null || region.isRopeRegion) {
            return null;
        }

        BotNavigationGraph.Segment bestSegment = null;
        Point bestPoint = null;
        int bestScore = Integer.MAX_VALUE;
        boolean foundContainingSegment = false;
        for (BotNavigationGraph.Segment segment : region.segments) {
            int dx = distanceToSegmentX(segment, x);
            boolean containsX = segment.containsX(x);
            if (!containsX && dx > REGION_STITCH_GAP_PX) {
                continue;
            }
            if (containsX) {
                foundContainingSegment = true;
            }
        }

        for (BotNavigationGraph.Segment segment : region.segments) {
            int dx = distanceToSegmentX(segment, x);
            boolean containsX = segment.containsX(x);
            if (!containsX && (foundContainingSegment || dx > REGION_STITCH_GAP_PX)) {
                continue;
            }

            Point candidate = segment.pointAt(x);
            int dy = candidate.y - referenceY;
            if (dy > cfg.MAX_SNAP_DROP || dy < -cfg.MAX_SLOPE_UP) {
                continue;
            }

            int score = dx * 1000 + Math.abs(dy);
            if (bestPoint == null
                    || score < bestScore
                    || (score == bestScore && candidate.y > bestPoint.y)) {
                bestSegment = segment;
                bestPoint = candidate;
                bestScore = score;
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

    static void setBuildWalkRegionLookup(MapleMap map,
                                         Map<Integer, BotNavigationGraph.Region> regionsById,
                                         Map<Integer, Integer> regionIdByFootholdId,
                                         Map<Integer, Foothold> footholdsById) {
        if (map == null || regionsById == null || regionIdByFootholdId == null || footholdsById == null) {
            ACTIVE_BUILD_WALK_REGION_LOOKUP.remove();
            return;
        }
        ACTIVE_BUILD_WALK_REGION_LOOKUP.set(new WalkRegionLookup(map.getId(), regionsById, regionIdByFootholdId, footholdsById));
    }

    static void clearBuildWalkRegionLookup() {
        ACTIVE_BUILD_WALK_REGION_LOOKUP.remove();
    }

    private static WalkRegionLookup resolveWalkRegionLookup(MapleMap map) {
        if (map == null) {
            return null;
        }

        WalkRegionLookup activeLookup = ACTIVE_BUILD_WALK_REGION_LOOKUP.get();
        if (activeLookup != null && activeLookup.mapId() == map.getId()) {
            return activeLookup;
        }

        BotNavigationGraph graph = BotNavigationGraphProvider.peekGraph(map);
        if (graph == null) {
            return null;
        }

        return new WalkRegionLookup(map.getId(), graph.regionsById, graph.regionIdByFootholdId, footholdsById(map));
    }

    private static Map<Integer, Foothold> footholdsById(MapleMap map) {
        if (map == null || map.getFootholds() == null) {
            return Map.of();
        }

        return FOOTHOLDS_BY_ID_BY_MAP_ID.computeIfAbsent(map.getId(), ignored -> {
            Map<Integer, Foothold> footholdsById = new HashMap<>();
            for (Foothold foothold : map.getFootholds().getAllFootholds()) {
                footholdsById.put(foothold.getId(), foothold);
            }
            return footholdsById;
        });
    }

    private static GroundStepPreview previewGroundStep(MapleMap map, Point currentPos, Foothold foothold, int nextX) {
        if (map == null || currentPos == null) {
            return null;
        }

        boolean constrainToWalkRegion = hasWalkRegion(map, foothold);
        Point standingPoint = constrainToWalkRegion
                ? findWalkRegionGroundPoint(map, foothold, currentPos.x, currentPos.y)
                : null;
        if (standingPoint == null) {
            standingPoint = findGroundPoint(map, currentPos);
        }

        int baseY = standingPoint != null
                && Math.abs(standingPoint.y - currentPos.y) <= cfg.MAX_SLOPE_UP
                ? standingPoint.y
                : currentPos.y;

        AirCollision wall = findGroundWallCollision(map, currentPos, new Point(nextX, baseY));
        if (wall.type() == AirCollisionType.WALL) {
            return new GroundStepPreview(baseY, currentPos, foothold, false, true);
        }

        Point snappedPoint;
        Foothold snappedFoothold;
        boolean lostGround;
        if (constrainToWalkRegion) {
            GroundRegionSample snappedSample = findWalkRegionGroundSample(map, foothold, nextX, baseY);
            snappedPoint = snappedSample == null ? null : snappedSample.point();
            snappedFoothold = snappedSample == null ? null : snappedSample.foothold();
            lostGround = snappedPoint == null || snappedPoint.y > baseY + cfg.MAX_SNAP_DROP;
        } else {
            int probeY = Math.max(currentPos.y, baseY + 1);
            snappedPoint = findGroundPoint(map, new Point(nextX, probeY));
            lostGround = snappedPoint == null || snappedPoint.y > baseY + cfg.MAX_SNAP_DROP;
            snappedFoothold = snappedPoint == null || map.getFootholds() == null
                    ? null
                    : map.getFootholds().findBelow(new Point(nextX, snappedPoint.y + 1));
        }

        return new GroundStepPreview(baseY, snappedPoint, snappedFoothold, lostGround, false);
    }

    private static int distanceToSegmentX(BotNavigationGraph.Segment segment, int x) {
        if (segment.containsX(x)) {
            return 0;
        }
        return x < segment.minX ? segment.minX - x : x - segment.maxX;
    }

    static void stopGroundMotion(BotEntry entry) {
        entry.hspeed = 0.0;
    }

    static void resetMotion(BotEntry entry, Point position) {
        clearMovementState(entry, position);
        syncCharacterState(entry);
    }

    static void teleportTo(BotEntry entry, Character bot, Point position) {
        bot.setPosition(position);
        clearMovementState(entry, position);
        syncCharacterState(entry);
    }

    static void markDead(BotEntry entry, Character bot) {
        clearMovementState(entry, bot.getPosition());
        syncCharacterState(entry);
    }

    static void idleOnGround(BotEntry entry, Character bot) {
        Point position = bot.getPosition();
        entry.inAir = false;
        entry.climbing = false;
        entry.climbRope = null;
        entry.crouching = false;
        entry.climbUpIntent = false;
        entry.velY = 0f;
        entry.airVelX = 0;
        entry.airSteerVelX = 0.0;
        entry.fixedAirArc = false;
        entry.lastDesiredDirection = 0;
        entry.physX = position.x;
        entry.physY = position.y;
        stopGroundMotion(entry);
        setMovementVelocity(entry, 0, 0);
        syncCharacterState(entry);
    }

    static void proneOnGround(BotEntry entry, Character bot) {
        idleOnGround(entry, bot);
        entry.crouching = true;
        entry.downJumpPending = false;
        syncCharacterState(entry);
    }

    static void queueDownJump(BotEntry entry, Character bot) {
        idleOnGround(entry, bot);
        entry.downJumpPending = true;
        entry.crouching = true;
        syncCharacterState(entry);
    }

    static void beginGroundJump(BotEntry entry, Character bot, int airVelX) {
        entry.blockedRopeGrab = null;
        launchAirborne(entry, bot, bot.getPosition(), -jumpForcePerTick(entry.movementProfile), airVelX, false);
    }

    static void beginClimbUpJump(BotEntry entry, Character bot, int airVelX) {
        entry.blockedRopeGrab = null;
        launchAirborne(entry, bot, bot.getPosition(), -jumpForcePerTick(entry.movementProfile), airVelX, true);
    }

    static void beginJumpOffRope(BotEntry entry, Character bot, int airVelX) {
        entry.blockedRopeGrab = null;
        launchAirborne(entry, bot, bot.getPosition(), -ropeJumpForcePerTick(entry.movementProfile), airVelX, false);
    }

    static void beginRopeTransferJump(BotEntry entry, Character bot, Rope sourceRope, int airVelX) {
        entry.blockedRopeGrab = sourceRope;
        launchAirborne(entry, bot, bot.getPosition(), -ropeJumpForcePerTick(entry.movementProfile), airVelX, true);
    }

    static void beginDownJump(BotEntry entry, Character bot) {
        entry.blockedRopeGrab = null;
        launchAirborne(entry, bot, bot.getPosition(), -downJumpForcePerTick(), 0, false);
        entry.downJumpGracePeriodMS = cfg.DOWN_JUMP_GRACE_MS;
    }

    /** Called by navigation when a DROP edge is executed — bot intentionally walks off a ledge. */
    static void executeDrop(BotEntry entry, Character bot, int airVelX) {
        beginFall(entry, bot, airVelX);
    }

    private static void beginFall(BotEntry entry, Character bot, int airVelX) {
        beginFall(entry, bot, bot.getPosition(), airVelX);
    }

    private static void beginFall(BotEntry entry, Character bot, Point position, int airVelX) {
        entry.blockedRopeGrab = null;
        bot.setPosition(new Point(position));
        launchAirborne(entry, bot, position, 0f, airVelX, false);
    }

    static void beginKnockback(BotEntry entry, Character bot, Point position, float initialVelY, int airVelX) {
        int preservedFacingDir = entry.facingDir;
        bot.setPosition(position);
        entry.blockedRopeGrab = null;
        launchAirborne(entry, bot, position, initialVelY, airVelX, true);
        entry.facingDir = preservedFacingDir;
        syncCharacterState(entry);
    }

    static void applyAirKnockback(BotEntry entry, Character bot, int airVelX) {
        int preservedFacingDir = entry.facingDir;
        Point position = bot.getPosition();
        entry.inAir = true;
        entry.climbing = false;
        entry.climbRope = null;
        entry.crouching = false;
        entry.physX = position.x;
        entry.physY = position.y;
        stopGroundMotion(entry);
        entry.climbUpIntent = true;
        entry.airVelX = airVelX;
        entry.airSteerVelX = 0.0;
        entry.fixedAirArc = false;
        entry.downJumpPending = false;
        entry.blockedRopeGrab = null;
        setMovementVelocity(entry, velocityFromDeltaX(airVelX), velocityFromAirStep(entry.velY));
        entry.facingDir = preservedFacingDir;
        syncCharacterState(entry);
    }

    private static void landOnGround(BotEntry entry, Character bot, Point position) {
        landOnGround(entry, bot, position, null, 0.0, 0.0);
    }

    private static void landOnGround(BotEntry entry,
                             Character bot,
                             Point position,
                             Foothold foothold,
                             double incomingDeltaX,
                             double incomingDeltaY) {
        bot.setPosition(position);
        entry.inAir = false;
        entry.climbing = false;
        entry.climbRope = null;
        entry.crouching = false;
        entry.climbUpIntent = false;
        entry.velY = 0f;
        entry.airVelX = 0;
        entry.airSteerVelX = 0.0;
        entry.fixedAirArc = false;
        entry.physX = position.x;
        entry.physY = position.y;
        entry.downJumpPending = false;
        entry.downJumpGracePeriodMS = 0L;
        entry.groundPhysicsCarryMs = 0.0;
        entry.blockedRopeGrab = null;
        entry.hspeed = landingGroundHSpeed(bot.getMap(), foothold, incomingDeltaX, incomingDeltaY, entry.movementProfile);
        setMovementVelocity(entry, velocityFromDeltaX(tickDeltaFromGroundHSpeed(bot.getMap(), entry.hspeed, entry.movementProfile)), 0);
        syncCharacterState(entry);
    }

    static void attachToRope(BotEntry entry, Character bot, Rope rope, int y) {
        int ropeY = Math.clamp(y, rope.topY(), rope.bottomY());
        setClimbPosition(entry, bot, rope, ropeY);
    }

    static void advanceClimb(BotEntry entry, Character bot, int verticalDir) {
        Rope rope = entry.climbRope;
        if (rope == null) {
            beginFall(entry, bot, 0);
            return;
        }

        int climbDir = Integer.compare(verticalDir, 0);
        if (climbDir == 0) {
            holdClimb(entry, bot);
            return;
        }

        int nextY = bot.getPosition().y + climbDir * climbStepPerTick();
        if (resolveClimbBoundary(entry, bot, rope, nextY)) {
            return;
        }

        setClimbPosition(entry, bot, rope, nextY);
    }

    static void holdClimb(BotEntry entry, Character bot) {
        Rope rope = entry.climbRope;
        if (rope == null) {
            beginFall(entry, bot, 0);
            return;
        }
        if (resolveClimbBoundary(entry, bot, rope, bot.getPosition().y)) {
            return;
        }

        setMovementVelocity(entry, 0, 0);
        syncCharacterState(entry);
    }

    static void tickMotionTimers(BotEntry entry) {
        if (entry.downJumpGracePeriodMS > 0L) {
            entry.downJumpGracePeriodMS = Math.max(0L, entry.downJumpGracePeriodMS - cfg.TICK_MS);
        }
    }

    static boolean canLand(BotEntry entry) {
        return entry.downJumpGracePeriodMS == 0L;
    }

    static GroundMotion applyGroundMotion(BotEntry entry, Character bot, Foothold foothold, int desiredDir) {
        MapleMap map = bot.getMap();
        Point currentPos = bot.getPosition();
        GroundStepResult step = simulateGroundMotion(map, currentPos, foothold, desiredDir,
                new GroundTravelState(entry.physX, entry.hspeed, entry.groundPhysicsCarryMs), entry.movementProfile);

        // Snap-up to a *different* foothold means the bot walked off the edge and a separate
        // platform happens to be within MAX_SLOPE_UP above. That is not an uphill slope of the
        // current foothold - the bot should fall, not jump up to the unconnected platform.
        if (step.lostGround()) {
            beginFall(entry, bot, step.point(), step.stepX());
            return new GroundMotion(step.stepX(), true);
        }

        Point position = step.point();
        bot.setPosition(position);
        entry.inAir = false;
        entry.climbing = false;
        entry.climbRope = null;
        entry.crouching = false;
        entry.climbUpIntent = false;
        entry.velY = 0f;
        entry.airVelX = 0;
        entry.airSteerVelX = 0.0;
        entry.fixedAirArc = false;
        entry.physX = position.x;
        entry.physY = position.y;
        entry.hspeed = step.state().hspeed();
        entry.groundPhysicsCarryMs = step.state().carryMs();
        entry.downJumpPending = false;
        setMovementVelocity(entry, step.velocityX(), 0);
        syncCharacterState(entry);
        return new GroundMotion(step.stepX(), false);
    }

    static GroundTravelState initialGroundTravelState(Point position) {
        return new GroundTravelState(position.x, 0.0, 0.0);
    }

    static GroundStepResult simulateGroundMotion(MapleMap map,
                                                 Point currentPos,
                                                 Foothold foothold,
                                                 int desiredDir,
                                                 GroundTravelState state,
                                                 BotMovementProfile profile) {
        if (map == null || currentPos == null || foothold == null || state == null) {
            return new GroundStepResult(currentPos, foothold, state, 0, 0, true);
        }

        GroundTravelState displaced = applyGroundDisplacement(map, foothold, desiredDir, state, profile);
        int newX = (int) Math.round(displaced.physX());
        int stepX = newX - currentPos.x;
        GroundStepPreview preview = previewGroundStep(map, currentPos, foothold, newX);
        if (preview == null) {
            return new GroundStepResult(currentPos, foothold, state, 0, 0, true);
        }

        if (preview.blocked()) {
            return new GroundStepResult(currentPos, foothold, initialGroundTravelState(currentPos), 0, 0, false);
        }

        if (preview.lostGround()) {
            return new GroundStepResult(new Point(newX, preview.baseY()), foothold, displaced,
                    stepX, velocityFromDeltaX(displaced.physX() - currentPos.x), true);
        }

        return new GroundStepResult(preview.point(), preview.foothold() != null ? preview.foothold() : foothold, displaced,
                stepX, velocityFromDeltaX(displaced.physX() - currentPos.x), false);
    }

    static WalkOffLanding simulateWalkOffLanding(MapleMap map,
                                                 Point from,
                                                 int desiredDir,
                                                 BotMovementProfile profile) {
        return simulateWalkOffLanding(map, from, desiredDir, initialGroundTravelState(from), profile);
    }

    static WalkOffLanding simulateWalkOffLanding(MapleMap map,
                                                 Point from,
                                                 int desiredDir,
                                                 GroundTravelState initialState,
                                                 BotMovementProfile profile) {
        if (map == null || from == null || desiredDir == 0 || initialState == null) {
            return null;
        }

        Foothold foothold = findGroundFoothold(map, from);
        if (foothold == null) {
            return null;
        }

        Point cursor = new Point(from);
        Foothold currentFoothold = foothold;
        GroundTravelState state = initialState;
        int elapsedMs = 0;
        for (int i = 0; i < 256; i++) {
            GroundStepResult step = simulateGroundMotion(map, cursor, currentFoothold, desiredDir, state, profile);
            if (step.lostGround()) {
                if (step.stepX() == 0) {
                    return null;
                }
                JumpLanding landing = simulateFallLanding(map, step.point(), step.stepX());
                if (landing == null) {
                    return null;
                }
                return new WalkOffLanding(new Point(step.point()), step.stepX(), landing,
                        elapsedMs + estimateFallLandingTimeMs(map, step.point(), step.stepX()));
            }

            cursor = step.point();
            currentFoothold = step.foothold();
            state = step.state();
            elapsedMs += cfg.TICK_MS;
        }
        return null;
    }

    static PostLandingJump simulatePostLandingGroundTicks(MapleMap map,
                                                          JumpLanding landing,
                                                          int desiredDir,
                                                          BotMovementProfile profile,
                                                          int ticks) {
        if (landing == null) {
            return null;
        }
        if (map == null || landing.point() == null || landing.foothold() == null || ticks <= 0) {
            return new PostLandingJump(landing,
                    landing.point() == null ? null : new Point(landing.point()),
                    landing.foothold(), false);
        }

        double landingHSpeed = landingGroundHSpeed(map, landing.foothold(),
                landing.incomingDeltaX(), landing.incomingDeltaY(), profile);
        GroundTravelState state = new GroundTravelState(landing.point().x, landingHSpeed, 0.0);
        Point cursor = new Point(landing.point());
        Foothold currentFoothold = landing.foothold();
        for (int i = 0; i < ticks; i++) {
            GroundStepResult step = simulateGroundMotion(map, cursor, currentFoothold, desiredDir, state, profile);
            if (step.lostGround()) {
                return new PostLandingJump(landing, step.point(), step.foothold(), true);
            }
            cursor = step.point();
            currentFoothold = step.foothold();
            state = step.state();
        }
        return new PostLandingJump(landing, cursor, currentFoothold, false);
    }

    private static Point roundedAirPosition(BotEntry entry) {
        return new Point((int) Math.round(entry.physX), (int) Math.round(entry.physY));
    }

    static void applyAirSteering(BotEntry entry, int targetDx) {
        if (targetDx == 0) return;
        double accel = targetDx > 0 ? cfg.AIR_STEER_ACCEL : -cfg.AIR_STEER_ACCEL;
        entry.airSteerVelX = Math.clamp(entry.airSteerVelX + accel, -cfg.AIR_STEER_MAX, cfg.AIR_STEER_MAX);
        // Client jump stance follows the held steering direction, not the preserved horizontal
        // launch momentum. Updating facing here makes airborne debug output line up with what the
        // client is visually trying to do, even before the net X velocity changes sign.
        entry.facingDir = targetDx > 0 ? 1 : -1;
    }

    private static Point advanceAirbornePosition(BotEntry entry, Character bot) {
        entry.physX += entry.airVelX + entry.airSteerVelX;
        float gravity = gravityPerTick();
        entry.physY += entry.velY + 0.5f * gravity;
        entry.velY = Math.min(entry.velY + gravity, maxFallPerTick());

        return roundedAirPosition(entry);
    }

    private static void applyAirbornePosition(BotEntry entry, Character bot, Point position) {
        bot.setPosition(position);
        entry.inAir = true;
        entry.climbing = false;
        entry.climbRope = null;
        entry.crouching = false;
        setMovementVelocity(entry, velocityFromDeltaX(entry.airVelX), velocityFromAirStep(entry.velY));
        syncCharacterState(entry);
    }

    /**
     * One physics step for an airborne bot: advance position, resolve wall/floor collision, apply result.
     * All collision outcome methods (landOnGround, collideWithAirWall, applyAirbornePosition) are
     * private — movement must not call them directly.
     */
    static AirborneStepResult stepAirborne(BotEntry entry, Character bot) {
        Point previousPos = roundedAirPosition(entry);
        Point nextPos = advanceAirbornePosition(entry, bot);
        AirCollision collision = resolveAirCollision(bot.getMap(), previousPos, nextPos);
        if (collision.type() == AirCollisionType.WALL) {
            collideWithAirWall(entry, bot, collision.point());
            return AirborneStepResult.WALL;
        }
        if (collision.type() == AirCollisionType.LAND && canLand(entry)) {
            landOnGround(entry, bot, collision.point(), collision.foothold(),
                    nextPos.x - previousPos.x, nextPos.y - previousPos.y);
            return AirborneStepResult.LANDED;
        }
        applyAirbornePosition(entry, bot, nextPos);
        return AirborneStepResult.CONTINUE;
    }

    private static void collideWithAirWall(BotEntry entry, Character bot, Point collisionPoint) {
        entry.airVelX = 0;
        entry.airSteerVelX = 0.0;
        entry.fixedAirArc = false;
        entry.physX = collisionPoint.x;
        entry.physY = collisionPoint.y;
        bot.setPosition(collisionPoint);
        entry.inAir = true;
        entry.climbing = false;
        entry.climbRope = null;
        entry.crouching = false;
        setMovementVelocity(entry, 0, velocityFromAirStep(entry.velY));
        syncCharacterState(entry);
    }

    static MovementSnapshot movementSnapshot(BotEntry entry) {
        int stance = resolveStance(entry);
        if (entry.bot != null && entry.bot.getStance() != stance) {
            entry.bot.setStance(stance);
        }
        return new MovementSnapshot(entry.movementVelX, entry.movementVelY, stance);
    }

    static int resolveStance(BotEntry entry) {
        Character bot = entry.bot;
        if (bot != null && bot.getHp() <= 0) {
            return resolveDeadStance(entry);
        }
        if (entry.climbing) {
            return entry.climbRope != null && entry.climbRope.isLadder()
                    ? CharacterStance.LADDER_STANCE
                    : CharacterStance.ROPE_STANCE;
        }
        if (entry.crouching) {
            return entry.facingDir >= 0 ? CharacterStance.PRONE_RIGHT_STANCE : CharacterStance.PRONE_LEFT_STANCE;
        }
        if (entry.inAir) {
            return entry.facingDir >= 0 ? CharacterStance.JUMP_RIGHT_STANCE : CharacterStance.JUMP_LEFT_STANCE;
        }
        if (entry.lastDesiredDirection > 0) {
            return CharacterStance.WALK_RIGHT_STANCE;
        }
        if (entry.lastDesiredDirection < 0) {
            return CharacterStance.WALK_LEFT_STANCE;
        }
        return resolveIdleGroundStance(entry);
    }

    static int resolveIdleGroundStance(BotEntry entry) {
        return entry.facingDir >= 0 ? CharacterStance.STAND_RIGHT_STANCE : CharacterStance.STAND_LEFT_STANCE;
    }

    static int resolveDeadStance(BotEntry entry) {
        return entry.facingDir >= 0 ? CharacterStance.DEAD_RIGHT_STANCE : CharacterStance.DEAD_LEFT_STANCE;
    }

    static boolean isStandingStance(int stance) {
        return CharacterStance.isStanding(stance);
    }

    static void syncCharacterState(BotEntry entry) {
        Character bot = entry.bot;
        if (bot == null) {
            return;
        }
        bot.setStance(resolveStance(entry));
    }

    static float calculateMaxJumpHeight() {
        return calculateMaxJumpHeight(BotMovementProfile.base());
    }

    static float calculateMaxJumpHeight(BotMovementProfile profile) {
        float jumpForce = jumpForcePerTick(profile);
        return jumpForce * jumpForce / (2 * gravityPerTick());
    }

    static int maxJumpHorizontalTravel(MapleMap map) {
        return maxJumpHorizontalTravel(map, BotMovementProfile.base());
    }

    static int maxJumpHorizontalTravel(MapleMap map, BotMovementProfile profile) {
        return maxHorizontalTravel(map, profile, jumpForcePerTick(profile));
    }

    static int maxRopeJumpHorizontalTravel(MapleMap map) {
        return maxRopeJumpHorizontalTravel(map, BotMovementProfile.base());
    }

    static int maxRopeJumpHorizontalTravel(MapleMap map, BotMovementProfile profile) {
        return maxHorizontalTravel(map, profile, ropeJumpForcePerTick(profile));
    }

    static int maxRopeGrabSimulationHorizontalTravel(MapleMap map, BotMovementProfile profile) {
        int maxTicks = Math.max(1, 1500 / cfg.TICK_MS);
        return walkStep(map, profile) * maxTicks;
    }

    static Point simulateRopeJumpGrab(MapleMap map, Point from, int stepX, Rope targetRope) {
        return simulateRopeJumpGrab(map, from, stepX, targetRope, BotMovementProfile.base());
    }

    static Point simulateRopeJumpGrab(MapleMap map, Point from, int stepX, Rope targetRope, BotMovementProfile profile) {
        return simulateRopeGrab(map, from, -ropeJumpForcePerTick(profile), stepX, targetRope, 0L);
    }

    static Point simulateGroundJumpRopeGrab(MapleMap map, Point from, int stepX, Rope targetRope) {
        return simulateGroundJumpRopeGrab(map, from, stepX, targetRope, BotMovementProfile.base());
    }

    static Point simulateGroundJumpRopeGrab(MapleMap map, Point from, int stepX, Rope targetRope, BotMovementProfile profile) {
        return simulateRopeGrab(map, from, -jumpForcePerTick(profile), stepX, targetRope, 0L);
    }

    static Point simulateDownJumpRopeGrab(MapleMap map, Point from, Rope targetRope) {
        return simulateRopeGrab(map, from, -downJumpForcePerTick(), 0, targetRope, cfg.DOWN_JUMP_GRACE_MS);
    }

    static boolean canReachRopeFromGround(MapleMap map, Point from, Rope rope) {
        return canReachRopeFromGround(map, from, rope, BotMovementProfile.base());
    }

    static boolean canReachRopeFromGround(MapleMap map, Point from, Rope rope, BotMovementProfile profile) {
        int dx = Math.abs(rope.x() - from.x);
        if (dx <= cfg.ROPE_GRAB_X && from.y >= rope.topY() && from.y <= rope.bottomY()) {
            return true;
        }
        if (rope.topY() >= from.y) {
            return false;
        }

        int jumpReach = (int) Math.ceil(calculateMaxJumpHeight(profile));
        return rope.bottomY() >= from.y - jumpReach
                && dx <= maxJumpHorizontalTravel(map, profile);
    }

    static JumpLanding simulateJumpLanding(MapleMap map, Point from, int stepX) {
        return simulateJumpLanding(map, from, stepX, BotMovementProfile.base());
    }

    static JumpLanding simulateJumpLanding(MapleMap map, Point from, int stepX, BotMovementProfile profile) {
        return simulateLanding(map, from, -jumpForcePerTick(profile), stepX, 0L);
    }

    static PostLandingJump simulateJumpLandingWithPostLandingTicks(MapleMap map,
                                                                   Point from,
                                                                   int stepX,
                                                                   BotMovementProfile profile,
                                                                   int postLandingTicks) {
        JumpLanding landing = simulateJumpLanding(map, from, stepX, profile);
        if (landing == null) {
            return null;
        }
        return simulatePostLandingGroundTicks(map, landing, Integer.compare(stepX, 0), profile, postLandingTicks);
    }

    static JumpLanding simulateDownJumpLanding(MapleMap map, Point from) {
        return simulateLanding(map, from, -downJumpForcePerTick(), 0, cfg.DOWN_JUMP_GRACE_MS);
    }

    static JumpLanding simulateFallLanding(MapleMap map, Point from, int stepX) {
        return simulateLanding(map, from, 0f, stepX, 0L);
    }

    static JumpLanding simulateRopeJumpLanding(MapleMap map, Point from, int stepX) {
        return simulateRopeJumpLanding(map, from, stepX, BotMovementProfile.base());
    }

    static JumpLanding simulateRopeJumpLanding(MapleMap map, Point from, int stepX, BotMovementProfile profile) {
        return simulateLanding(map, from, -ropeJumpForcePerTick(profile), stepX, 0L);
    }

    static int estimateJumpLandingTimeMs(MapleMap map, Point from, int stepX) {
        return estimateJumpLandingTimeMs(map, from, stepX, BotMovementProfile.base());
    }

    static int estimateJumpLandingTimeMs(MapleMap map, Point from, int stepX, BotMovementProfile profile) {
        return estimateLandingTimeMs(map, from, -jumpForcePerTick(profile), stepX, 0L);
    }

    static int estimateDownJumpLandingTimeMs(MapleMap map, Point from) {
        return estimateLandingTimeMs(map, from, -downJumpForcePerTick(), 0, cfg.DOWN_JUMP_GRACE_MS);
    }

    static int estimateFallLandingTimeMs(MapleMap map, Point from, int stepX) {
        return estimateLandingTimeMs(map, from, 0f, stepX, 0L);
    }

    static int estimateRopeJumpLandingTimeMs(MapleMap map, Point from, int stepX) {
        return estimateRopeJumpLandingTimeMs(map, from, stepX, BotMovementProfile.base());
    }

    static int estimateRopeJumpLandingTimeMs(MapleMap map, Point from, int stepX, BotMovementProfile profile) {
        return estimateLandingTimeMs(map, from, -ropeJumpForcePerTick(profile), stepX, 0L);
    }

    static int estimateGroundJumpRopeGrabTimeMs(MapleMap map, Point from, int stepX, Rope targetRope) {
        return estimateGroundJumpRopeGrabTimeMs(map, from, stepX, targetRope, BotMovementProfile.base());
    }

    static int estimateGroundJumpRopeGrabTimeMs(MapleMap map, Point from, int stepX, Rope targetRope, BotMovementProfile profile) {
        return estimateRopeGrabTimeMs(map, from, -jumpForcePerTick(profile), stepX, targetRope, 0L);
    }

    static int estimateDownJumpRopeGrabTimeMs(MapleMap map, Point from, Rope targetRope) {
        return estimateRopeGrabTimeMs(map, from, -downJumpForcePerTick(), 0, targetRope, cfg.DOWN_JUMP_GRACE_MS);
    }

    static int estimateRopeJumpGrabTimeMs(MapleMap map, Point from, int stepX, Rope targetRope) {
        return estimateRopeJumpGrabTimeMs(map, from, stepX, targetRope, BotMovementProfile.base());
    }

    static int estimateRopeJumpGrabTimeMs(MapleMap map, Point from, int stepX, Rope targetRope, BotMovementProfile profile) {
        return estimateRopeGrabTimeMs(map, from, -ropeJumpForcePerTick(profile), stepX, targetRope, 0L);
    }

    private static JumpLanding findAirLanding(MapleMap map, Point previousPos, Point nextPos) {
        AirCollision collision = resolveAirCollision(map, previousPos, nextPos);
        return collision.type() == AirCollisionType.LAND
                ? new JumpLanding(collision.point(), collision.foothold(),
                nextPos.x - previousPos.x, nextPos.y - previousPos.y)
                : null;
    }

    private static AirCollision resolveAirCollision(MapleMap map, Point previousPos, Point nextPos) {
        if (map == null || map.getFootholds() == null || previousPos == null || nextPos == null) {
            return AirCollision.none();
        }
        AirCollision wall = findWallCollision(map, previousPos, nextPos);
        AirCollision landing = findGroundCollision(map, previousPos, nextPos);
        if (wall.type == AirCollisionType.NONE) {
            return landing;
        }
        if (landing.type == AirCollisionType.NONE) {
            return wall;
        }
        return wall.progress <= landing.progress ? wall : landing;
    }

    private static void launchAirborne(BotEntry entry,
                                       Character bot,
                                       Point position,
                                       float initialVelY,
                                       int airVelX,
                                       boolean climbUpIntent) {
        entry.climbing = false;
        entry.climbRope = null;
        entry.inAir = true;
        entry.crouching = false;
        entry.physX = position.x;
        entry.physY = position.y;
        entry.velY = initialVelY;
        stopGroundMotion(entry);
        entry.climbUpIntent = climbUpIntent;
        entry.airVelX = airVelX;
        entry.airSteerVelX = 0.0;
        entry.fixedAirArc = false;
        entry.downJumpPending = false;
        setMovementVelocity(entry, velocityFromDeltaX(airVelX), velocityFromAirStep(initialVelY));
        syncCharacterState(entry);
    }

    private static boolean resolveClimbBoundary(BotEntry entry, Character bot, Rope rope, int candidateY) {
        if (candidateY <= rope.topY()) {
            Point landing = findTopLandingPoint(bot, rope, candidateY);
            if (landing != null) {
                landOnGround(entry, bot, landing);
            } else {
                // Top of a rope always connects to a foothold in valid map data.
                // If none is found, clamp to topY and hold rather than falling — the bot will
                // recover on re-path. Falling here would cause the oscillation bug where the bot
                // climbs to the top, falls, re-grabs the rope, and loops indefinitely.
                setClimbPosition(entry, bot, rope, rope.topY());
            }
            return true;
        }
        if (candidateY > rope.bottomY()) {
            beginFall(entry, bot, 0);
            return true;
        }
        return false;
    }

    private static Point findTopLandingPoint(Character bot, Rope rope, int candidateY) {
        MapleMap map = bot.getMap();
        if (map == null) {
            return null;
        }

        int probeY = Math.min(candidateY, rope.topY()) - 3;
        Point ground = map.getPointBelow(new Point(rope.x(), probeY));
        if (ground == null) {
            return null;
        }

        return ground.y <= rope.topY() + climbStepPerTick() + 2 ? ground : null;
    }

    private static void setClimbPosition(BotEntry entry, Character bot, Rope rope, int y) {
        Point position = new Point(rope.x(), y);
        bot.setPosition(position);
        entry.climbing = true;
        entry.climbRope = rope;
        entry.inAir = false;
        entry.crouching = false;
        entry.climbUpIntent = false;
        entry.velY = 0f;
        entry.airVelX = 0;
        entry.airSteerVelX = 0.0;
        entry.fixedAirArc = false;
        entry.physX = position.x;
        entry.physY = position.y;
        entry.downJumpPending = false;
        stopGroundMotion(entry);
        setMovementVelocity(entry, 0, 0);
        syncCharacterState(entry);
    }

    private static void clearMovementState(BotEntry entry, Point position) {
        entry.inAir = false;
        entry.climbing = false;
        entry.climbRope = null;
        entry.crouching = false;
        entry.velY = 0f;
        entry.hspeed = 0.0;
        entry.physX = position.x;
        entry.physY = position.y;
        entry.groundPhysicsCarryMs = 0.0;
        entry.airVelX = 0;
        entry.airSteerVelX = 0.0;
        entry.fixedAirArc = false;
        entry.wasMovingX = false;
        entry.lastDesiredDirection = 0;
        entry.climbUpIntent = false;
        entry.blockedRopeGrab = null;
        entry.ropeGrabCooldownMs = 0;
        entry.downJumpPending = false;
        entry.downJumpGracePeriodMS = 0L;
        setMovementVelocity(entry, 0, 0);
    }

    private static void setMovementVelocity(BotEntry entry, int velX, int velY) {
        entry.movementVelX = velX;
        entry.movementVelY = velY;
        if (velX != 0) {
            entry.facingDir = velX > 0 ? 1 : -1;
        }
    }

    private static int velocityFromAirStep(float airVelPerTick) {
        return Math.round(airVelPerTick * (1000f / cfg.TICK_MS));
    }

    private static GroundTravelState applyGroundDisplacement(MapleMap map,
                                                             Foothold foothold,
                                                             int desiredDir,
                                                             GroundTravelState state,
                                                             BotMovementProfile profile) {
        GroundStepCounter counter = groundPhysicsSteps(state.carryMs(), map);
        if (counter.steps() == 0) {
            return state;
        }

        double physX = state.physX();
        double hspeed = state.hspeed();
        for (int i = 0; i < counter.steps(); i++) {
            hspeed = applyGroundPhysicsStep(hspeed, foothold, desiredDir, profile);
            physX += hspeed;
        }
        return new GroundTravelState(physX, hspeed, counter.carryMs());
    }

    private record GroundStepCounter(int steps, double carryMs) {
    }

    private static GroundStepCounter groundPhysicsSteps(double carryMs, MapleMap map) {
        double nextCarryMs = carryMs + cfg.TICK_MS * mapGroundSpeedScale(map);
        int steps = (int) (nextCarryMs / CLIENT_GROUND_STEP_MS);
        nextCarryMs -= steps * CLIENT_GROUND_STEP_MS;
        return new GroundStepCounter(steps, nextCarryMs);
    }

    private static double applyGroundPhysicsStep(double hspeed, Foothold foothold, int desiredDir, BotMovementProfile profile) {
        double hforce = desiredDir * maxHForcePerClientStep(profile);
        if (hforce == 0.0 && Math.abs(hspeed) < 0.1) {
            return 0.0;
        }

        double inertia = hspeed / cfg.GROUNDSLIP;
        double slope = clampedSlope(foothold);
        double drag = (cfg.FRICTION + cfg.SLOPEFACTOR * (1.0 + slope * -inertia)) * inertia;
        return hspeed + hforce - drag;
    }

    private static double clampedSlope(Foothold foothold) {
        if (foothold == null) {
            return 0.0;
        }
        return Math.clamp(foothold.slope(), -0.5, 0.5);
    }

    // True if a step between two endpoint positions is physically walkable (same criteria as
    // graph walk-edge generation, so physics and graph agree on which transitions are valid).
    static boolean isWalkableEndpointStep(int dx, int dy) {
        return dx <= WALK_GAP_PX
                && dy <= cfg.MAX_SNAP_DROP
                && dy >= -cfg.MAX_SLOPE_UP;
    }

    private static double mapGroundSpeedScale(MapleMap map) {
        float footholdSpeed = map.getFootholdSpeed();
        if (footholdSpeed <= 0.0f) {
            return 1.0;
        }
        return footholdSpeed;
    }

    private static double maxHForcePerClientStep(BotMovementProfile profile) {
        return profileOrBase(profile).hForcePxs() * CLIENT_GROUND_STEP_S;
    }

    private static double maxHSpeedPerClientStep(BotMovementProfile profile) {
        return maxHForcePerClientStep(profile) * cfg.GROUNDSLIP / (cfg.FRICTION + cfg.SLOPEFACTOR);
    }

    private static int maxHorizontalTravel(MapleMap map, BotMovementProfile profile, float launchSpeedPerTick) {
        int airtimeTicks = Math.max(1, (int) Math.ceil((2 * launchSpeedPerTick) / gravityPerTick()));
        return walkStep(map, profile) * airtimeTicks;
    }

    private static AirCollision findGroundCollision(MapleMap map, Point previousPos, Point nextPos) {
        if (nextPos.y < previousPos.y) {
            return AirCollision.none();
        }

        int startX = previousPos.x;
        int endX = nextPos.x;
        int dir = Integer.compare(endX, startX);
        if (dir == 0) {
            return landingAtX(map, previousPos, nextPos, endX, 1.0);
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

    private static AirCollision findWallCollision(MapleMap map, Point previousPos, Point nextPos) {
        return findWallCollision(map, previousPos, nextPos, false);
    }

    private static AirCollision findGroundWallCollision(MapleMap map, Point previousPos, Point nextPos) {
        return findWallCollision(map, previousPos, nextPos, true);
    }

    private static AirCollision findWallCollision(MapleMap map,
                                                  Point previousPos,
                                                  Point nextPos,
                                                  boolean allowWalkableGroundEndpoint) {
        if (map == null || map.getFootholds() == null) {
            return AirCollision.none();
        }
        if (previousPos.x == nextPos.x) {
            return AirCollision.none();
        }

        java.util.Set<Integer> collidableWalls = getCollidableWallIds(map);
        AirCollision best = mapSideBoundaryCollision(map, previousPos, nextPos);
        for (Foothold foothold : map.getFootholds().getAllFootholds()) {
            if (!foothold.isWall() || !collidableWalls.contains(foothold.getId())) {
                continue;
            }
            AirCollision collision = wallCollision(foothold, previousPos, nextPos, allowWalkableGroundEndpoint);
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
        int boundaryX = dir > 0 ? area.x + area.width : area.x;
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

    /**
     * Returns the collidable wall set from the nav graph if available, otherwise computes it
     * directly from the foothold tree.  This avoids a circular dependency when the nav graph
     * is still being built.
     */
    private static java.util.Set<Integer> getCollidableWallIds(MapleMap map) {
        java.util.Set<Integer> cached = BotNavigationGraphProvider.getCachedCollidableWallIds(map.getId());
        if (cached != null) {
            return cached;
        }
        java.util.List<Foothold> all = map.getFootholds().getAllFootholds();
        java.util.Map<Integer, Foothold> byId = new java.util.HashMap<>(all.size());
        for (Foothold fh : all) {
            byId.put(fh.getId(), fh);
        }
        java.util.Set<Integer> result = new java.util.HashSet<>();
        for (Foothold fh : all) {
            if (Foothold.isCollidableWall(fh, byId)) {
                result.add(fh.getId());
            }
        }
        return result;
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

        // Catch tangential landings at the jump apex when the next platform sits exactly at the
        // previous Y. Keep this forward-only so drops and down-jumps do not instantly re-land on
        // the takeoff foothold they just left.
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
        // Ignore the wall exactly at the takeoff point; this is the ledge-edge case
        // where a drop would otherwise look like an immediate side collision.
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

    private static boolean isWalkableGroundWallEndpoint(double yAtWall, int minY, int maxY) {
        if (Math.abs(yAtWall - minY) < 0.001) {
            return true;
        }
        return Math.abs(yAtWall - maxY) < 0.001 && maxY - minY <= cfg.MAX_SLOPE_UP;
    }

    private static double landingGroundHSpeed(MapleMap map,
                                              Foothold foothold,
                                              double incomingDeltaX,
                                              double incomingDeltaY,
                                              BotMovementProfile profile) {
        double landingDeltaX = incomingDeltaX;
        if (foothold != null && !foothold.isWall() && foothold.slope() != 0.0) {
            double tangentX = foothold.getX2() - foothold.getX1();
            double tangentY = foothold.getY2() - foothold.getY1();
            double tangentLength = Math.hypot(tangentX, tangentY);
            if (tangentLength > 0.0) {
                double unitX = tangentX / tangentLength;
                double unitY = tangentY / tangentLength;
                double dot = incomingDeltaX * unitX + incomingDeltaY * unitY;
                landingDeltaX = unitX * dot;
            }
        }

        double maxDeltaPerTick = Math.max(1.0, walkStep(map, profile));
        landingDeltaX = Math.clamp(landingDeltaX, -maxDeltaPerTick, maxDeltaPerTick);
        return groundHSpeedFromTickDelta(map, landingDeltaX, profile);
    }

    private static double groundHSpeedFromTickDelta(MapleMap map, double deltaXPerTick, BotMovementProfile profile) {
        double stepsPerTick = Math.max(1.0, (cfg.TICK_MS * mapGroundSpeedScale(map)) / CLIENT_GROUND_STEP_MS);
        return Math.clamp(deltaXPerTick / stepsPerTick, -maxHSpeedPerClientStep(profile), maxHSpeedPerClientStep(profile));
    }

    private static double tickDeltaFromGroundHSpeed(MapleMap map, double groundHSpeed, BotMovementProfile profile) {
        double stepsPerTick = Math.max(1.0, (cfg.TICK_MS * mapGroundSpeedScale(map)) / CLIENT_GROUND_STEP_MS);
        double clampedHSpeed = Math.clamp(groundHSpeed, -maxHSpeedPerClientStep(profile), maxHSpeedPerClientStep(profile));
        return clampedHSpeed * stepsPerTick;
    }

    private static RopeGrabResult simulateRopeGrabCore(MapleMap map,
                                                       Point from,
                                                       float initialVelY,
                                                       int stepX,
                                                       Rope targetRope,
                                                       long landingGraceMs) {
        if (targetRope == null) {
            return null;
        }

        float velocityY = initialVelY;
        double physX = from.x;
        double physY = from.y;
        int previousIntY = from.y;
        long remainingLandingGraceMs = Math.max(0L, landingGraceMs);
        final float gravity = gravityPerTick();
        final float maxFall = maxFallPerTick();

        for (int tick = 0; tick < (1500 / cfg.TICK_MS); tick++) {
            Point current = new Point((int) Math.round(physX), (int) Math.round(physY));
            if (canGrabRopeAtPoint(current, targetRope)) {
                return new RopeGrabResult(new Point(targetRope.x(), current.y), tick);
            }

            if (remainingLandingGraceMs > 0L) {
                remainingLandingGraceMs = Math.max(0L, remainingLandingGraceMs - cfg.TICK_MS);
            }

            physX += stepX;
            physY += velocityY + 0.5f * gravity;
            velocityY = Math.min(velocityY + gravity, maxFall);

            int x = (int) Math.round(physX);
            int intY = (int) Math.round(physY);
            AirCollision collision = resolveAirCollision(map, new Point((int) Math.round(physX - stepX), previousIntY),
                    new Point(x, intY));
            if (collision.type() == AirCollisionType.WALL) {
                physX = collision.point().x;
                physY = collision.point().y;
                stepX = 0;
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

    private static Point simulateRopeGrab(MapleMap map,
                                          Point from,
                                          float initialVelY,
                                          int stepX,
                                          Rope targetRope,
                                          long landingGraceMs) {
        RopeGrabResult result = simulateRopeGrabCore(map, from, initialVelY, stepX, targetRope, landingGraceMs);
        return result != null ? result.point() : null;
    }

    private static int estimateRopeGrabTimeMs(MapleMap map,
                                              Point from,
                                              float initialVelY,
                                              int stepX,
                                              Rope targetRope,
                                              long landingGraceMs) {
        RopeGrabResult result = simulateRopeGrabCore(map, from, initialVelY, stepX, targetRope, landingGraceMs);
        return result != null ? result.ticks() * cfg.TICK_MS : Integer.MAX_VALUE;
    }

    private static boolean canGrabRopeAtPoint(Point position, Rope rope) {
        return Math.abs(position.x - rope.x()) <= cfg.ROPE_GRAB_X
                && position.y >= rope.topY()
                && position.y <= rope.bottomY();
    }

    private static JumpLanding simulateLanding(MapleMap map,
                                               Point from,
                                               float initialVelY,
                                               int stepX,
                                               long landingGraceMs) {
        float velocityY = initialVelY;
        double physX = from.x;
        double physY = from.y;
        int previousIntY = from.y;
        long remainingLandingGraceMs = Math.max(0L, landingGraceMs);
        final float gravity = gravityPerTick();
        final float maxFall = maxFallPerTick();

        for (int tick = 0; tick < (1500 / cfg.TICK_MS); tick++) {
            if (remainingLandingGraceMs > 0L) {
                remainingLandingGraceMs = Math.max(0L, remainingLandingGraceMs - cfg.TICK_MS);
            }

            physX += stepX;
            physY += velocityY + 0.5f * gravity;
            velocityY = Math.min(velocityY + gravity, maxFall);

            int x = (int) Math.round(physX);
            int intY = (int) Math.round(physY);
            Point previousPoint = new Point((int) Math.round(physX - stepX), previousIntY);
            Point nextPoint = new Point(x, intY);
            AirCollision collision = resolveAirCollision(map, previousPoint, nextPoint);
            if (collision.type() == AirCollisionType.WALL) {
                physX = collision.point().x;
                physY = collision.point().y;
                stepX = 0;
                previousIntY = collision.point().y;
                continue;
            }
            if (collision.type() == AirCollisionType.LAND && remainingLandingGraceMs == 0L) {
                return new JumpLanding(collision.point(), collision.foothold(),
                        nextPoint.x - previousPoint.x, nextPoint.y - previousPoint.y, tick + 1);
            }

            previousIntY = intY;
        }

        return null;
    }

    private static int estimateLandingTimeMs(MapleMap map,
                                             Point from,
                                             float initialVelY,
                                             int stepX,
                                             long landingGraceMs) {
        JumpLanding landing = simulateLanding(map, from, initialVelY, stepX, landingGraceMs);
        return landing != null ? landing.timeMs() : Integer.MAX_VALUE;
    }
}
