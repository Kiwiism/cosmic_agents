package server.bots;

import client.Character;
import server.maps.Foothold;
import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.*;

final class BotPhysicsEngine {
    private static final double CLIENT_GROUND_STEP_MS = 8.0;
    private static final double CLIENT_GROUND_STEP_S = CLIENT_GROUND_STEP_MS / 1000.0;

    static class Config {
        public int TICK_MS = 50;

        public int WALK_VEL = 150;

        public float GRAVITY_PXS2 = 2187.5f;
        public float JUMP_SPEED_PXS = 562.5f;
        public float JUMP_DOWN_PXS = 320.0f;
        public float JUMP_ROPE_PXS = 375.0f;
        public float MAX_FALL_PXS = 670.0f;
        public double HFORCE_PXS = 20.0;
        public double GROUNDSLIP = 3.0;
        public double FRICTION = 0.3;
        public double SLOPEFACTOR = 0.1;

        public float CLIMB_SPEED_PXS = 100.0f;
        public int ROPE_GRAB_X = 22;
        public int MAX_SNAP_DROP = 16;
        public int MAX_SLOPE_UP = 26;
        public int DOWN_JUMP_GRACE_MS = 350;

        public int DEAD_STANCE = 0;
        public int WALK_RIGHT_STANCE = 2;
        public int WALK_LEFT_STANCE = 3;
        public int STAND_STANCE = 5;
        public int JUMP_RIGHT_STANCE = 6;
        public int JUMP_LEFT_STANCE = 7;
        public int PRONE_STANCE = 10;
        public int ROPE_STANCE = 16;
        public int LADDER_STANCE = 17;
    }

    record GroundMotion(int stepX, boolean lostGround) {
    }

    record MovementSnapshot(int velX, int velY, int stance) {
    }

    static final class JumpLanding {
        private final Point point;
        private final Foothold foothold;

        JumpLanding(Point point, Foothold foothold) {
            this.point = point;
            this.foothold = foothold;
        }

        Point point() {
            return point;
        }

        Foothold foothold() {
            return foothold;
        }
    }

    static Config cfg = new Config();

    private BotPhysicsEngine() {
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

    static float downJumpForcePerTick() {
        return cfg.JUMP_DOWN_PXS * tickS();
    }

    static float ropeJumpForcePerTick() {
        return cfg.JUMP_ROPE_PXS * tickS();
    }

    static int climbStepPerTick() {
        return Math.max(1, Math.round(cfg.CLIMB_SPEED_PXS * tickS()));
    }

    static float gravityPerTick() {
        float t = tickS();
        return cfg.GRAVITY_PXS2 * t * t;
    }

    static int walkStep(MapleMap map) {
        double step = maxHSpeedPerClientStep() * cfg.TICK_MS * mapGroundSpeedScale(map) / CLIENT_GROUND_STEP_MS;
        return Math.max(1, (int) Math.round(step));
    }

    static int velocityFromDeltaX(double deltaX) {
        return (int) Math.round(deltaX * (1000.0 / cfg.TICK_MS));
    }

    static void syncGroundPosition(BotEntry entry, int x) {
        if (entry.hspeed == 0.0 && (int) Math.round(entry.physX) != x) {
            entry.physX = x;
        }
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
        entry.physX = position.x;
        entry.physY = position.y;
        stopGroundMotion(entry);
        setMovementVelocity(entry, 0, 0);
        syncCharacterState(entry);
    }

    static void queueDownJump(BotEntry entry, Character bot) {
        idleOnGround(entry, bot);
        entry.downJumpPending = true;
        entry.crouching = true;
        syncCharacterState(entry);
    }

    static void beginGroundJump(BotEntry entry, Character bot, int airVelX) {
        launchAirborne(entry, bot, bot.getPosition(), -jumpForcePerTick(), airVelX, false);
    }

    static void beginClimbUpJump(BotEntry entry, Character bot, int airVelX) {
        launchAirborne(entry, bot, bot.getPosition(), -jumpForcePerTick(), airVelX, true);
    }

    static void beginJumpOffRope(BotEntry entry, Character bot, int airVelX) {
        launchAirborne(entry, bot, bot.getPosition(), -ropeJumpForcePerTick(), airVelX, false);
    }

    static void beginDownJump(BotEntry entry, Character bot) {
        launchAirborne(entry, bot, bot.getPosition(), -downJumpForcePerTick(), 0, false);
        entry.downJumpGracePeriodMS = cfg.DOWN_JUMP_GRACE_MS;
    }

    static void beginFall(BotEntry entry, Character bot, int airVelX) {
        launchAirborne(entry, bot, bot.getPosition(), 0f, airVelX, false);
    }

    static void beginKnockback(BotEntry entry, Character bot, Point position, float initialVelY, int airVelX) {
        bot.setPosition(position);
        launchAirborne(entry, bot, position, initialVelY, airVelX, false);
    }

    static void landOnGround(BotEntry entry, Character bot, Point position) {
        bot.setPosition(position);
        idleOnGround(entry, bot);
    }

    static void attachToRope(BotEntry entry, Character bot, Rope rope, int y) {
        int ropeY = Math.max(rope.topY(), Math.min(y, rope.bottomY()));
        Point position = new Point(rope.x(), ropeY);
        bot.setPosition(position);
        entry.climbing = true;
        entry.climbRope = rope;
        entry.inAir = false;
        entry.crouching = false;
        entry.climbUpIntent = false;
        entry.velY = 0f;
        entry.airVelX = 0;
        entry.physX = position.x;
        entry.physY = position.y;
        entry.downJumpPending = false;
        stopGroundMotion(entry);
        setMovementVelocity(entry, 0, 0);
        syncCharacterState(entry);
    }

    static void moveOnRope(BotEntry entry, Character bot, int y) {
        Rope rope = entry.climbRope;
        if (rope == null) {
            return;
        }

        int ropeY = Math.max(rope.topY(), Math.min(y, rope.bottomY()));
        Point position = new Point(rope.x(), ropeY);
        bot.setPosition(position);
        entry.climbing = true;
        entry.inAir = false;
        entry.crouching = false;
        entry.climbUpIntent = false;
        entry.velY = 0f;
        entry.airVelX = 0;
        entry.physX = position.x;
        entry.physY = position.y;
        setMovementVelocity(entry, 0, 0);
        syncCharacterState(entry);
    }

    static void holdClimb(BotEntry entry) {
        setMovementVelocity(entry, 0, 0);
        syncCharacterState(entry);
    }

    static void tickMotionTimers(BotEntry entry) {
        if (entry.ropeGrabCooldownMs > 0) {
            entry.ropeGrabCooldownMs = Math.max(0, entry.ropeGrabCooldownMs - cfg.TICK_MS);
        }
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
        double deltaPhysX = applyGroundDisplacement(entry, map, foothold, desiredDir);
        int velocityX = velocityFromDeltaX(deltaPhysX);
        int newX = (int) Math.round(entry.physX);
        int stepX = newX - currentPos.x;
        Point snappedPoint = map.getPointBelow(new Point(newX, currentPos.y - cfg.MAX_SLOPE_UP));
        boolean lostGround = snappedPoint == null || snappedPoint.y > currentPos.y + cfg.MAX_SNAP_DROP;

        if (lostGround) {
            abortGroundMotion(entry, bot);
            return new GroundMotion(0, true);
        }

        Point position = stepX == 0 ? currentPos : snappedPoint;
        bot.setPosition(position);
        entry.inAir = false;
        entry.climbing = false;
        entry.climbRope = null;
        entry.crouching = false;
        entry.climbUpIntent = false;
        entry.velY = 0f;
        entry.airVelX = 0;
        entry.physX = position.x;
        entry.physY = position.y;
        entry.downJumpPending = false;
        setMovementVelocity(entry, velocityX, 0);
        syncCharacterState(entry);
        return new GroundMotion(stepX, false);
    }

    static void abortGroundMotion(BotEntry entry, Character bot) {
        Point position = bot.getPosition();
        entry.inAir = false;
        entry.climbing = false;
        entry.climbRope = null;
        entry.crouching = false;
        entry.climbUpIntent = false;
        entry.velY = 0f;
        entry.airVelX = 0;
        entry.physX = position.x;
        entry.physY = position.y;
        entry.downJumpPending = false;
        stopGroundMotion(entry);
        setMovementVelocity(entry, 0, 0);
        syncCharacterState(entry);
    }

    static Point roundedAirPosition(BotEntry entry) {
        return new Point((int) Math.round(entry.physX), (int) Math.round(entry.physY));
    }

    static Point advanceAirbornePosition(BotEntry entry, Character bot) {
        entry.physX += entry.airVelX;
        float gravity = gravityPerTick();
        entry.physY += entry.velY + 0.5f * gravity;
        entry.velY = Math.min(entry.velY + gravity, maxFallPerTick());

        Point nextPosition = roundedAirPosition(entry);
        bot.setPosition(nextPosition);
        entry.inAir = true;
        entry.climbing = false;
        entry.climbRope = null;
        entry.crouching = false;
        setMovementVelocity(entry, velocityFromDeltaX(entry.airVelX), velocityFromAirStep(entry.velY));
        syncCharacterState(entry);
        return nextPosition;
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
            return cfg.DEAD_STANCE;
        }
        if (entry.climbing) {
            return entry.climbRope != null && entry.climbRope.isLadder()
                    ? cfg.LADDER_STANCE
                    : cfg.ROPE_STANCE;
        }
        if (entry.crouching) {
            return cfg.PRONE_STANCE;
        }
        if (entry.inAir) {
            return entry.facingDir >= 0 ? cfg.JUMP_RIGHT_STANCE : cfg.JUMP_LEFT_STANCE;
        }
        if (entry.movementVelX > 0) {
            return cfg.WALK_RIGHT_STANCE;
        }
        if (entry.movementVelX < 0) {
            return cfg.WALK_LEFT_STANCE;
        }
        return cfg.STAND_STANCE;
    }

    static void syncCharacterState(BotEntry entry) {
        Character bot = entry.bot;
        if (bot == null) {
            return;
        }
        bot.setStance(resolveStance(entry));
    }

    static float calculateMaxJumpHeight() {
        float jumpForce = jumpForcePerTick();
        return jumpForce * jumpForce / (2 * gravityPerTick());
    }

    static int maxJumpHorizontalTravel(MapleMap map) {
        return maxHorizontalTravel(map, jumpForcePerTick());
    }

    static int maxRopeJumpHorizontalTravel(MapleMap map) {
        return maxHorizontalTravel(map, ropeJumpForcePerTick());
    }

    static boolean canReachRopeFromGround(MapleMap map, Point from, Rope rope) {
        int dx = Math.abs(rope.x() - from.x);
        if (dx <= cfg.ROPE_GRAB_X && from.y >= rope.topY() && from.y <= rope.bottomY()) {
            return true;
        }
        if (rope.topY() >= from.y) {
            return false;
        }

        int jumpReach = (int) calculateMaxJumpHeight();
        return rope.bottomY() >= from.y - jumpReach
                && dx <= maxJumpHorizontalTravel(map);
    }

    static JumpLanding simulateJumpLanding(MapleMap map, Point from, int stepX) {
        return simulateLanding(map, from, -jumpForcePerTick(), stepX);
    }

    static JumpLanding simulateRopeJumpLanding(MapleMap map, Point from, int stepX) {
        return simulateLanding(map, from, -ropeJumpForcePerTick(), stepX);
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
        entry.downJumpPending = false;
        setMovementVelocity(entry, velocityFromDeltaX(airVelX), velocityFromAirStep(initialVelY));
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
        entry.wasMovingX = false;
        entry.climbUpIntent = false;
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

    private static double applyGroundDisplacement(BotEntry entry, MapleMap map, Foothold foothold, int desiredDir) {
        int steps = groundPhysicsSteps(entry, map);
        if (steps == 0) {
            return 0.0;
        }

        double startX = entry.physX;
        for (int i = 0; i < steps; i++) {
            applyGroundPhysicsStep(entry, foothold, desiredDir);
            entry.physX += entry.hspeed;
        }
        return entry.physX - startX;
    }

    private static int groundPhysicsSteps(BotEntry entry, MapleMap map) {
        entry.groundPhysicsCarryMs += cfg.TICK_MS * mapGroundSpeedScale(map);
        int steps = (int) (entry.groundPhysicsCarryMs / CLIENT_GROUND_STEP_MS);
        entry.groundPhysicsCarryMs -= steps * CLIENT_GROUND_STEP_MS;
        return steps;
    }

    private static void applyGroundPhysicsStep(BotEntry entry, Foothold foothold, int desiredDir) {
        double hforce = desiredDir * maxHForcePerClientStep();
        if (hforce == 0.0 && Math.abs(entry.hspeed) < 0.1) {
            entry.hspeed = 0.0;
            return;
        }

        double inertia = entry.hspeed / cfg.GROUNDSLIP;
        double slope = clampedSlope(foothold);
        double drag = (cfg.FRICTION + cfg.SLOPEFACTOR * (1.0 + slope * -inertia)) * inertia;
        entry.hspeed += hforce - drag;
    }

    private static double clampedSlope(Foothold foothold) {
        if (foothold == null) {
            return 0.0;
        }
        return Math.max(-0.5, Math.min(0.5, foothold.slope()));
    }

    private static double mapGroundSpeedScale(MapleMap map) {
        float footholdSpeed = map.getFootholdSpeed();
        if (footholdSpeed <= 0.0f) {
            return 1.0;
        }
        return footholdSpeed;
    }

    private static double maxHForcePerClientStep() {
        return cfg.HFORCE_PXS * CLIENT_GROUND_STEP_S;
    }

    private static double maxHSpeedPerClientStep() {
        return maxHForcePerClientStep() * cfg.GROUNDSLIP / (cfg.FRICTION + cfg.SLOPEFACTOR);
    }

    private static int maxHorizontalTravel(MapleMap map, float launchSpeedPerTick) {
        int airtimeTicks = Math.max(1, (int) Math.ceil((2 * launchSpeedPerTick) / gravityPerTick()));
        return walkStep(map) * airtimeTicks;
    }

    private static JumpLanding simulateLanding(MapleMap map, Point from, float initialVelY, int stepX) {
        float velocityY = initialVelY;
        double physX = from.x;
        double physY = from.y;
        int previousIntY = from.y;

        for (int tick = 0; tick < (1500 / cfg.TICK_MS); tick++) {
            physX += stepX;
            float gravity = gravityPerTick();
            physY += velocityY + 0.5f * gravity;
            velocityY = Math.min(velocityY + gravity, maxFallPerTick());

            int x = (int) Math.round(physX);
            int intY = (int) Math.round(physY);
            if (velocityY > 0) {
                Point probe = new Point(x, previousIntY + 1);
                Point floor = map.getPointBelow(probe);
                if (floor != null && floor.y <= intY) {
                    Foothold foothold = map.getFootholds().findBelow(probe);
                    if (foothold != null) {
                        return new JumpLanding(new Point(x, floor.y), foothold);
                    }
                }
            }

            previousIntY = intY;
        }

        return null;
    }
}
