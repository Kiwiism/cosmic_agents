package server.bots;

import client.Character;
import io.netty.buffer.Unpooled;
import net.packet.ByteBufInPacket;
import net.packet.InPacket;
import net.packet.Packet;
import server.maps.Foothold;
import server.maps.MapleMap;
import server.maps.Rope;
import tools.PacketCreator;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

class BotMovementManager {
    private static final double CLIENT_GROUND_STEP_MS = 8.0;
    private static final double CLIENT_GROUND_STEP_S = CLIENT_GROUND_STEP_MS / 1000.0;

    static class Config {
        // Tick — the only tick-coupled value; everything else is in real-world units
        public int   TICK_MS      = 50;   // ms between movement simulation ticks

        // Movement (px/s for velocities; px distances are tick-independent)
        public int   WALK_VEL     = 150;   // px/s for movement packet broadcast
        public int   STOP_DIST    = 30;
        public int   FOLLOW_DIST  = 80;

        // Physics — velocities in px/s, accelerations in px/s²
        // Reference: OpenStory Constants.h TIMESTEP=8ms, Physics.cpp GRAVFORCE=0.14,
        //            Player.cpp get_jumpforce()=4.5 and get_walkforce() at 100 SPEED/JUMP.
        // Velocities scale linearly with tick:   per_tick = value * (TICK_MS/1000)
        // Accelerations scale quadratically:     per_tick = value * (TICK_MS/1000)²
        public float GRAVITY_PXS2      = 2187.5f; // 0.14 / 0.008² — matches OpenStory GRAVFORCE
        public float JUMP_SPEED_PXS    = 562.5f;  // 4.5  / 0.008  — get_jumpforce() at 100 JUMP
        public float JUMP_DOWN_PXS     = 320.0f;  // small downward push for platform drop-through
        public float JUMP_ROPE_PXS     = 375.0f;  // 3.0  / 0.008  — rope jump = jump_force / 1.5
        public float MAX_FALL_PXS      = 670.0f;  // terminal fall speed
        // OpenStory ground friction model (Physics.cpp)
        // hforce applied each tick; inertia drag = FRICTION+SLOPEFACTOR * (hspeed/GROUNDSLIP)
        // Max hspeed reached when hforce == drag: hspeed_max = hforce * GROUNDSLIP / (FRICTION+SLOPEFACTOR)
        public double HFORCE_PXS   = 20.0;  // 0.16 px/8ms * (1000/8) — walk force per second
        public double GROUNDSLIP   = 3.0;   // OpenStory Physics.cpp
        public double FRICTION     = 0.3;   // OpenStory Physics.cpp
        public double SLOPEFACTOR  = 0.1;   // OpenStory Physics.cpp

        // Jump control
        public int   JUMP_Y_THRESH    = 30;
        public int   JUMP_COOLDOWN_MS = 1000;
        public int   ARC_LEAD_STEPS = 3;
        public int   MAX_SNAP_DROP  = 16;
        public int   MAX_SLOPE_UP   = 26;

        // Rope climbing
        public float CLIMB_SPEED_PXS  = 100.0f;  // rope climb speed (px/s)
        public int   ROPE_SEEK_X      = 150;
        public int   ROPE_GRAB_X      = 22;
        public int   TELEPORT_DIST    = 2000;

        // Stances
        public int   DEAD_STANCE  = 0;
        public int   STAND_STANCE = 5;
        public int   PRONE_STANCE = 10;
        public int   LEDGE_SEEK_X = 150;

        // Stuck recovery
        public int   STUCK_CHECK_INTERVAL_MS = 3000;
        public int   STUCK_CHASE_MS         = 6000;
        public int   STUCK_MIN_MOVE       = 20;
        public int   STUCK_WALKBACK_LIMIT = 200;

        // Waypoint
        public int   WAYPOINT_SEEK_X    = 1000;
        public int   WAYPOINT_TIMEOUT_MS = 8000;
        public int   WAYPOINT_MIN_DY    = 400;
    }

    static Config cfg = new Config();

    static int tickDown(int remainingMs) {
        if (remainingMs <= 0) {
            return 0;
        }
        return Math.max(0, remainingMs - cfg.TICK_MS);
    }

    static int delayAfterCurrentTick(int durationMs) {
        if (durationMs <= 0) {
            return 0;
        }
        return Math.max(0, durationMs - cfg.TICK_MS);
    }

    // t in seconds per tick
    static float tickS() { return cfg.TICK_MS / 1000f; }

    // Velocities (px/s) → px/tick: linear scaling
    static float maxFallPerTick()       { return cfg.MAX_FALL_PXS   * tickS(); }
    static float jumpForcePerTick()     { return cfg.JUMP_SPEED_PXS * tickS(); }
    static float downJumpForcePerTick() { return cfg.JUMP_DOWN_PXS  * tickS(); }
    static float ropeJumpForcePerTick() { return cfg.JUMP_ROPE_PXS  * tickS(); }
    static int   climbStepPerTick()     { return Math.max(1, Math.round(cfg.CLIMB_SPEED_PXS * tickS())); }

    // Accelerations (px/s²) → px/tick: quadratic scaling (a·t²)
    static float gravityPerTick() { float t = tickS(); return cfg.GRAVITY_PXS2 * t * t; }

    static double hForcePerClientStep() { return cfg.HFORCE_PXS * CLIENT_GROUND_STEP_S; }

    static double maxHSpeedPerClientStep() {
        return hForcePerClientStep() * cfg.GROUNDSLIP / (cfg.FRICTION + cfg.SLOPEFACTOR);
    }

    static double mapGroundSpeedScale(MapleMap map) {
        float footholdSpeed = map.getFootholdSpeed();
        if (footholdSpeed <= 0.0f) {
            return 1.0;
        }
        return footholdSpeed;
    }

    static int walkStep(MapleMap map) {
        double step = maxHSpeedPerClientStep() * cfg.TICK_MS * mapGroundSpeedScale(map) / CLIENT_GROUND_STEP_MS;
        return Math.max(1, (int) Math.round(step));
    }

    static int velocityFromDeltaX(double deltaX) {
        return (int) Math.round(deltaX * (1000.0 / cfg.TICK_MS));
    }

    static int groundPhysicsSteps(BotEntry entry, MapleMap map) {
        entry.groundPhysicsCarryMs += cfg.TICK_MS * mapGroundSpeedScale(map);
        int steps = (int) (entry.groundPhysicsCarryMs / CLIENT_GROUND_STEP_MS);
        entry.groundPhysicsCarryMs -= steps * CLIENT_GROUND_STEP_MS;
        return steps;
    }

    static double clampedSlope(Foothold foothold) {
        if (foothold == null) {
            return 0.0;
        }
        return Math.max(-0.5, Math.min(0.5, foothold.slope()));
    }

    static void applyGroundPhysicsStep(BotEntry entry, Foothold foothold, int desiredDir) {
        double hforce = desiredDir * hForcePerClientStep();
        if (hforce == 0.0 && Math.abs(entry.hspeed) < 0.1) {
            entry.hspeed = 0.0;
            return;
        }

        double inertia = entry.hspeed / cfg.GROUNDSLIP;
        double slopef = clampedSlope(foothold);
        double drag = (cfg.FRICTION + cfg.SLOPEFACTOR * (1.0 + slopef * -inertia)) * inertia;
        entry.hspeed += hforce - drag;
    }

    static double applyGroundPhysics(BotEntry entry, MapleMap map, Foothold foothold, int desiredDir) {
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

    static void stopGroundMotion(BotEntry entry) {
        entry.hspeed = 0.0;
    }

    static void resetEntryState(BotEntry entry) {
        entry.inAir             = true;
        entry.climbing          = false;
        entry.velY              = 0f;
        entry.hspeed            = 0.0;
        entry.physX             = 0.0;
        entry.physY             = 0.0;
        entry.groundPhysicsCarryMs = 0.0;
        entry.airVelX           = 0;
        entry.wasMovingX        = false;
        entry.seekingRope       = false;
        entry.ropeGrabCooldownMs = 0;
        entry.downJumpPending   = false;
        entry.rawChaseMs        = 0;
        entry.stuckCheckElapsedMs = 0;
        entry.lastStuckCheckPos = null;
        entry.waypointRope      = null;
        entry.grindTarget       = null;
        entry.attackCooldownMs  = 0;
    }

    // -------------------------------------------------------------------------
    // Rope climbing
    // -------------------------------------------------------------------------

    static void tickClimbing(BotEntry entry, Point targetPos, boolean runAiTick) {
        Config cfg = BotMovementManager.cfg;
        Character bot = entry.bot;
        Point botPos  = bot.getPosition();
        int dy      = targetPos.y - botPos.y;
        int dxOwner = targetPos.x - entry.climbRope.x();

        // Always: tick timers
        entry.jumpCooldownMs = tickDown(entry.jumpCooldownMs);

        // Always: boundary checks — position-reactive, must run every movement tick
        if (botPos.y <= entry.climbRope.topY()) {
            entry.climbing = false;
            Point ground = bot.getMap().getPointBelow(new Point(botPos.x, botPos.y - 3));
            if (ground != null && ground.y <= botPos.y + climbStepPerTick() + 2) {
                entry.inAir = false;
                entry.velY  = 0f;
                stopGroundMotion(entry);
                bot.setPosition(new Point(botPos.x, ground.y));
                bot.setStance(5);
                broadcastMovement(bot, 0, 0);
            } else {
                entry.inAir = true;
                entry.velY  = 0f;
            }
            return;
        }
        if (botPos.y >= entry.climbRope.bottomY() + 3) {
            entry.climbing = false;
            entry.inAir    = true;
            entry.velY     = 0f;
            bot.setStance(6);
            broadcastMovement(bot, 0, 0);
            return;
        }

        // AI: jump off rope / update idle state
        if (runAiTick) {
            if (Math.abs(dxOwner) > cfg.FOLLOW_DIST && entry.jumpCooldownMs == 0
                    && entry.climbRope.bottomY() < targetPos.y) {
                jumpOffRope(entry, bot, dxOwner);
                return;
            }
            entry.climbIdle = !entry.grinding
                    && Math.abs(dy) < cfg.FOLLOW_DIST
                    && Math.abs(dxOwner) < cfg.FOLLOW_DIST * 2;
        }

        // Always: apply climb step using cached idle state
        if (entry.climbIdle) {
            bot.setStance(entry.climbRope.isLadder() ? 17 : 16);
            broadcastMovement(bot, 0, 0);
            return;
        }
        int newY = (dy < 0) || (entry.climbRope.topY() < botPos.y + 200) ? botPos.y - climbStepPerTick()
                          : Math.min(botPos.y + climbStepPerTick(), entry.climbRope.bottomY());
        bot.setPosition(new Point(entry.climbRope.x(), newY));
        bot.setStance(entry.climbRope.isLadder() ? 17 : 16);
        broadcastMovement(bot, 0, 0);
    }

    /** Jump off the current rope position toward dx direction. */
    static void jumpOffRope(BotEntry entry, Character bot, int dx) {
        Config cfg = BotMovementManager.cfg;
        entry.climbing        = false;
        entry.inAir           = true;
        entry.velY            = -ropeJumpForcePerTick();
        stopGroundMotion(entry);
        entry.seekingRope     = false;
        int walkStep = walkStep(bot.getMap());
        entry.airVelX         = dx > 0 ? walkStep : dx < 0 ? -walkStep : 0;
        entry.ropeGrabCooldownMs = delayAfterCurrentTick(cfg.JUMP_COOLDOWN_MS + 200); // stay off rope long enough to clear it
        entry.jumpCooldownMs    = delayAfterCurrentTick(cfg.JUMP_COOLDOWN_MS);
        bot.setStance(dx >= 0 ? 6 : 7);
        int jumpVelY = Math.round(-cfg.JUMP_ROPE_PXS);
        broadcastMovement(bot, velocityFromDeltaX(entry.airVelX), jumpVelY);
    }

    // -------------------------------------------------------------------------
    // Airborne physics
    // -------------------------------------------------------------------------

    static void tickAirborne(BotEntry entry, Point targetPos) {
        Config cfg = BotMovementManager.cfg;
        if (entry.downJumpGracePeriodMS > 0L) {
            entry.downJumpGracePeriodMS = Math.max(0L, entry.downJumpGracePeriodMS - cfg.TICK_MS);
        }

        Character bot = entry.bot;
        Point botPos  = bot.getPosition();
        int newX      = botPos.x + entry.airVelX;

        // Mid-air rope catch — only when we intentionally jumped for a rope
        if (successfullyGrabbedRope(entry, bot, botPos, cfg)) return;

        entry.velY = Math.min(entry.velY + gravityPerTick(), maxFallPerTick());
        entry.physY += entry.velY;
        int newY = (int) Math.round(entry.physY);

        // Landing check — search strictly below current Y to avoid immediately re-landing
        // Also dont land after down jump grace period
        // on the foothold we just left (botPos.y + 1, not botPos.y - 1)
        if (entry.velY > 0 && entry.downJumpGracePeriodMS == 0) {
            Point floorPt = bot.getMap().getPointBelow(new Point(newX, botPos.y + 1));
            if (floorPt != null && floorPt.y <= newY) {
                entry.inAir       = false;
                entry.velY        = 0f;
                entry.physX       = newX;
                entry.physY       = floorPt.y;
                entry.jumpCooldownMs = 0;
                stopGroundMotion(entry);
                entry.seekingRope = false;
                entry.airVelX     = 0;
                bot.setPosition(new Point(newX, floorPt.y));
                int dx = targetPos.x - botPos.x;
                int dy = targetPos.y - botPos.y;
                bot.setStance(dx >= 0 ? 2 : 3);
                broadcastMovement(bot, 0, 0);
                return;
            }
        }

        entry.physY = newY; // keep in sync after rounding
        bot.setPosition(new Point(newX, newY));
        // Use locked airVelX for stance + broadcast — not the owner direction (which can flip mid-arc)
        int velXBcast = velocityFromDeltaX(entry.airVelX);
        bot.setStance(entry.airVelX >= 0 ? 6 : 7);
        int velYBcast = (int) (entry.velY * (1000f / cfg.TICK_MS));
        broadcastMovement(bot, velXBcast, velYBcast);
    }

    private static boolean successfullyGrabbedRope(BotEntry entry, Character bot, Point botPos, Config cfg) {
        entry.ropeGrabCooldownMs = tickDown(entry.ropeGrabCooldownMs);
        if (entry.seekingRope && entry.ropeGrabCooldownMs == 0) {
            for (Rope rope : bot.getMap().getRopes()) {
                if (Math.abs(rope.x() - botPos.x) <= cfg.ROPE_GRAB_X
                        && botPos.y >= rope.topY() && botPos.y <= rope.bottomY()) {
                    entry.climbing    = true;
                    entry.inAir       = false;
                    entry.velY        = 0f;
                    stopGroundMotion(entry);
                    entry.seekingRope = false;
                    entry.climbRope   = rope;
                    bot.setPosition(new Point(rope.x(), botPos.y));
                    bot.setStance(entry.climbRope.isLadder() ? 17 : 16);
                    broadcastMovement(bot, 0, 0);
                    return true;
                }
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Ground movement with foothold awareness
    // -------------------------------------------------------------------------

    static void tickGrounded(BotEntry entry, Point targetPos, boolean runAiTick) {
        Config cfg = BotMovementManager.cfg;
        Character bot = entry.bot;
        Point botPos  = bot.getPosition();
        int dx = targetPos.x - botPos.x;
        int dy = targetPos.y - botPos.y; // negative = owner is higher on screen

        // Sync physX to actual position when idle (after teleport, map change, or first ground tick)
        if (entry.hspeed == 0.0 && (int) Math.round(entry.physX) != botPos.x) {
            entry.physX = botPos.x;
        }

        // Always: validate foothold
        Foothold currentFh = bot.getMap().getFootholds()
                .findBelow(new Point(botPos.x, botPos.y - cfg.MAX_SLOPE_UP));
        if (currentFh == null) {
            entry.inAir = true;
            entry.velY  = 0f;
            stopGroundMotion(entry);
            return;
        }

        // Always: tick timers every movement tick so countdowns are accurate
        entry.jumpCooldownMs = tickDown(entry.jumpCooldownMs);
        entry.rawChaseMs     = tickDown(entry.rawChaseMs);
        if (entry.lastStuckCheckPos == null) entry.lastStuckCheckPos = botPos;
        entry.stuckCheckElapsedMs += cfg.TICK_MS;
        if (entry.waypointRope != null) entry.waypointTimerMs = tickDown(entry.waypointTimerMs);

        // Always: complete a pending down-jump (prone shown last logic tick)
        if (entry.downJumpPending) {
            entry.downJumpPending    = false;
            entry.downJumpGracePeriodMS = 300L;
            entry.inAir              = true;
            entry.physX              = botPos.x;
            entry.physY              = botPos.y;
            entry.velY               = -downJumpForcePerTick();
            entry.airVelX            = 0;
            entry.jumpCooldownMs     = delayAfterCurrentTick(cfg.JUMP_COOLDOWN_MS);
            bot.setPosition(new Point(botPos.x, botPos.y));
            bot.setStance(dx >= 0 ? 6 : 7);
            broadcastMovement(bot, 0, Math.round(entry.velY * (1000f / cfg.TICK_MS)));
            return;
        }

        // ── AI decisions (every LOGIC_TICK_MS) ───────────────────────────────
        if (runAiTick) {
            // Stuck detection
            if (entry.stuckCheckElapsedMs >= cfg.STUCK_CHECK_INTERVAL_MS) {
                int moved = Math.abs(botPos.x - entry.lastStuckCheckPos.x)
                        + Math.abs(botPos.y - entry.lastStuckCheckPos.y);
                if (moved < cfg.STUCK_MIN_MOVE && Math.abs(dx) > cfg.FOLLOW_DIST) {
                    entry.rawChaseMs = cfg.STUCK_CHASE_MS;
                }
                entry.stuckCheckElapsedMs = 0;
                entry.lastStuckCheckPos   = botPos;
            }

            // Waypoint navigation
            if (entry.waypointRope != null) {
                if (dy >= -cfg.JUMP_Y_THRESH * 2) {
                    entry.waypointRope = null; // owner no longer far above
                } else if (entry.waypointTimerMs == 0) {
                    entry.waypointRope = null; // timed out
                } else {
                    Rope wp  = entry.waypointRope;
                    int  wdx = wp.x() - botPos.x;
                    if (Math.abs(wdx) < cfg.ROPE_GRAB_X) {
                        entry.waypointRope = null;
                        if (botPos.y >= wp.topY() && botPos.y <= wp.bottomY()) {
                            stopGroundMotion(entry);
                            entry.climbing  = true;
                            entry.climbRope = wp;
                            bot.setPosition(new Point(wp.x(), botPos.y));
                            bot.setStance(entry.climbRope.isLadder() ? 17 : 16);
                            broadcastMovement(bot, 0, 0);
                        } else {
                            entry.jumpCooldownMs = delayAfterCurrentTick(cfg.JUMP_COOLDOWN_MS);
                            initiateRopeJump(entry, bot, wdx);
                        }
                        return;
                    }
                    int stepToWp = Math.min(Math.abs(wdx), walkStep(bot.getMap())) * (wdx >= 0 ? 1 : -1);
                    int walkX    = botPos.x + stepToWp;
                    Point snappedWp = bot.getMap().getPointBelow(new Point(walkX, botPos.y - cfg.MAX_SLOPE_UP));
                    if (snappedWp != null && snappedWp.y <= botPos.y + cfg.MAX_SNAP_DROP) {
                        bot.setPosition(new Point(walkX, snappedWp.y));
                        bot.setStance(wdx >= 0 ? 2 : 3);
                        broadcastMovement(bot, velocityFromDeltaX(stepToWp), 0);
                        return;
                    }
                    targetPos = new Point(wp.x(), wp.topY()); // redirect for proactive jump below
                }
            }

            // Down-jump initiation
            if (dy > cfg.JUMP_Y_THRESH * 3 && dy > Math.abs(dx) && entry.jumpCooldownMs == 0) {
                if (Math.abs(dx) > cfg.STOP_DIST) {
                    int stepLimit = walkStep(bot.getMap());
                    int sampleDir = dx > 0 ? stepLimit : -stepLimit;
                    int dropStepX = 0;
                    for (int i = 1; i * stepLimit <= cfg.LEDGE_SEEK_X; i++) {
                        Point sp = bot.getMap().getPointBelow(
                                new Point(botPos.x + sampleDir * i, botPos.y - cfg.MAX_SLOPE_UP));
                        if (sp == null || sp.y > botPos.y + cfg.MAX_SNAP_DROP) {
                            dropStepX = sampleDir;
                            break;
                        }
                    }
                    if (dropStepX != 0) {
                        int walkX = botPos.x + dropStepX;
                        Point walkSnapped = bot.getMap().getPointBelow(
                                new Point(walkX, botPos.y - cfg.MAX_SLOPE_UP));
                        if (walkSnapped != null && walkSnapped.y <= botPos.y + cfg.MAX_SNAP_DROP) {
                            bot.setPosition(new Point(walkX, walkSnapped.y));
                            bot.setStance(dropStepX > 0 ? 2 : 3);
                            broadcastMovement(bot, velocityFromDeltaX(dropStepX), 0);
                        } else {
                            entry.inAir   = true;
                            stopGroundMotion(entry);
                            entry.airVelX = dropStepX;
                            entry.velY    = 0f;
                        }
                        return;
                    }
                }
                entry.downJumpPending = true;
                bot.setStance(cfg.PRONE_STANCE);
                broadcastMovement(bot, 0, 0);
                return;
            }

            // Rope logic
            if (dy < -cfg.JUMP_Y_THRESH * 2 && Math.abs(dy) >= Math.abs(dx)) {
                Rope rope = findNearbyRope(bot, botPos);
                if (rope != null) {
                    int rdx = rope.x() - botPos.x;
                    if (Math.abs(rdx) < cfg.ROPE_GRAB_X
                            && botPos.y >= rope.topY() && botPos.y <= rope.bottomY()) {
                        stopGroundMotion(entry);
                        entry.climbing  = true;
                        entry.climbRope = rope;
                        bot.setPosition(new Point(rope.x(), botPos.y));
                        bot.setStance(entry.climbRope.isLadder() ? 17 : 16);
                        broadcastMovement(bot, 0, 0);
                        return;
                    }
                    if (entry.jumpCooldownMs == 0) {
                        int stepLimit = walkStep(bot.getMap());
                        int arcStep = dx >= 0 ? stepLimit : -stepLimit;
                        boolean jumpWorks = arcCheckJump(bot, botPos, arcStep, targetPos.x, targetPos.y)
                                || arcCheckJump(bot, botPos, -arcStep, targetPos.x, targetPos.y)
                                || arcCheckJump(bot, botPos, 0, targetPos.x, targetPos.y);
                        if (!jumpWorks) {
                            int maxHTravel = stepLimit * (int) (2 * jumpForcePerTick() / gravityPerTick());
                            if (Math.abs(rdx) <= maxHTravel) {
                                entry.jumpCooldownMs = delayAfterCurrentTick(cfg.JUMP_COOLDOWN_MS);
                                initiateRopeJump(entry, bot, rdx);
                                return;
                            }
                            int stepToRope = Math.min(Math.abs(rdx), stepLimit) * (rdx >= 0 ? 1 : -1);
                            int newXr = botPos.x + stepToRope;
                            Point snappedR = bot.getMap().getPointBelow(new Point(newXr, botPos.y - cfg.MAX_SLOPE_UP));
                            if (snappedR != null && snappedR.y <= botPos.y + cfg.MAX_SNAP_DROP) {
                                bot.setPosition(new Point(newXr, snappedR.y));
                                bot.setStance(rdx >= 0 ? 2 : 3);
                                broadcastMovement(bot, velocityFromDeltaX(stepToRope), 0);
                                return;
                            }
                        }
                    } else {
                        int stepToRope = Math.min(Math.abs(rdx), walkStep(bot.getMap())) * (rdx >= 0 ? 1 : -1);
                        int newXr = botPos.x + stepToRope;
                        Point snappedR = bot.getMap().getPointBelow(new Point(newXr, botPos.y - cfg.MAX_SLOPE_UP));
                        if (snappedR != null && snappedR.y <= botPos.y + cfg.MAX_SNAP_DROP) {
                            bot.setPosition(new Point(newXr, snappedR.y));
                            bot.setStance(rdx >= 0 ? 2 : 3);
                            broadcastMovement(bot, velocityFromDeltaX(stepToRope), 0);
                            return;
                        }
                    }
                }
            }

            // Proactive jump
            if (dy < -cfg.JUMP_Y_THRESH && entry.jumpCooldownMs == 0) {
                int stepXai   = calcStepX(entry, bot.getMap(), botPos.x, targetPos.x);
                boolean blocked  = stepXai == 0 || !isPathWalkable(bot, botPos, stepXai);
                boolean farAbove = dy < -cfg.JUMP_Y_THRESH * 2;
                if (blocked) {
                    int arcStep = stepXai != 0 ? stepXai : (dx >= 0 ? walkStep(bot.getMap()) : -walkStep(bot.getMap()));
                    int winDir  = Integer.MIN_VALUE;
                    if (arcCheckJump(bot, botPos, arcStep, targetPos.x, targetPos.y)) {
                        winDir = arcStep;
                    } else if (arcCheckJump(bot, botPos, 0, targetPos.x, targetPos.y)) {
                        winDir = 0;
                    } else if (farAbove) {
                        outer:
                        for (int lead = 1; lead <= cfg.ARC_LEAD_STEPS; lead++) {
                            for (int walkDir : new int[]{1, -1}) {
                                int leadX = botPos.x + arcStep * lead * walkDir;
                                Point leadGround = bot.getMap().getPointBelow(
                                        new Point(leadX, botPos.y - cfg.MAX_SLOPE_UP));
                                if (leadGround == null || leadGround.y > botPos.y + cfg.MAX_SNAP_DROP) continue;
                                Point leadPt = new Point(leadX, leadGround.y);
                                for (int jDir : new int[]{1, -1}) {
                                    if (arcCheckJump(bot, leadPt, arcStep * jDir, targetPos.x, targetPos.y)) {
                                        winDir = arcStep * jDir;
                                        break outer;
                                    }
                                }
                            }
                        }
                    }
                    if (winDir != Integer.MIN_VALUE) {
                        entry.jumpCooldownMs = delayAfterCurrentTick(cfg.JUMP_COOLDOWN_MS);
                        initiateJump(entry, bot, winDir);
                        return;
                    }
                    if (entry.rawChaseMs > 0) {
                        int backStep = -arcStep;
                        if (Math.abs(dx) <= cfg.STUCK_WALKBACK_LIMIT
                                && arcCheckJump(bot, botPos, backStep, targetPos.x, targetPos.y)) {
                            entry.jumpCooldownMs = delayAfterCurrentTick(cfg.JUMP_COOLDOWN_MS);
                            initiateJump(entry, bot, backStep);
                            return;
                        }
                    }
                    if (farAbove && -dy >= cfg.WAYPOINT_MIN_DY && entry.waypointRope == null) {
                        Rope wp = findWaypointRope(bot, botPos);
                        if (wp != null) {
                            entry.waypointRope    = wp;
                            entry.waypointTimerMs = delayAfterCurrentTick(cfg.WAYPOINT_TIMEOUT_MS);
                        }
                    }
                    entry.jumpCooldownMs = entry.rawChaseMs > 0
                            ? delayAfterCurrentTick(cfg.JUMP_COOLDOWN_MS)
                            : delayAfterCurrentTick(cfg.JUMP_COOLDOWN_MS * 3);
                }
            }

            // Cache desired direction for movement ticks between logic ticks
            boolean closeEnough = Math.abs(dx) < cfg.STOP_DIST && Math.abs(dy) < cfg.STOP_DIST;
            if (closeEnough) {
                entry.wasMovingX          = false;
                entry.lastDesiredDirection = 0;
            } else {
                entry.lastDesiredDirection = Integer.compare(calcStepX(entry, bot.getMap(), botPos.x, targetPos.x), 0);
            }
        } // end runAiTick

        // ── Always: apply walk step using cached direction ────────────────────
        double deltaPhysX = applyGroundPhysics(entry, bot.getMap(), currentFh, entry.lastDesiredDirection);
        int newXPhys = (int) Math.round(entry.physX);
        int stepX = newXPhys - botPos.x;
        int velX = velocityFromDeltaX(deltaPhysX);
        if (entry.lastDesiredDirection == 0 && stepX == 0 && velX == 0) {
            bot.setStance(5);
            broadcastMovement(bot, 0, 0);
            return;
        }
        if (stepX == 0 && velX != 0) {
            bot.setStance(velX > 0 ? 2 : 3);
            broadcastMovement(bot, velX, 0);
            return;
        }
        Point snapped = bot.getMap().getPointBelow(new Point(newXPhys, botPos.y - cfg.MAX_SLOPE_UP));
        if (snapped == null || snapped.y > botPos.y + cfg.MAX_SNAP_DROP) {
            if (dy > cfg.JUMP_Y_THRESH) {
                entry.inAir   = true;
                stopGroundMotion(entry);
                entry.airVelX = stepX;
                entry.velY    = 0f;
                entry.physY   = botPos.y;
            } else if (runAiTick && entry.jumpCooldownMs == 0 && stepX != 0
                    && arcCheckJump(bot, botPos, stepX, targetPos.x, targetPos.y)) {
                initiateJump(entry, bot, dx);
            } else {
                stopGroundMotion(entry);
                entry.physX = botPos.x;
                bot.setStance(5);
                broadcastMovement(bot, 0, 0);
            }
            return;
        }
        int newStance;
        if (stepX > 0)      { newStance = 2; }
        else if (stepX < 0) { newStance = 3; }
        else                 { newStance = 5; }
        bot.setPosition(new Point(newXPhys, snapped.y));
        bot.setStance(newStance);
        broadcastMovement(bot, velX, 0);
    }

    static float calculateMaxJumpHeight() {
        Config cfg = BotMovementManager.cfg;
        float jumpForce = jumpForcePerTick();
        return jumpForce * jumpForce / (2 * gravityPerTick());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the X step toward targetX, with hysteresis to prevent jitter:
     * only starts moving once distance exceeds FOLLOW_DIST, stops at STOP_DIST.
     */
    static int calcStepX(BotEntry entry, MapleMap map, int botX, int targetX) {
        Config cfg = BotMovementManager.cfg;
        int dx   = targetX - botX;
        int absDx = Math.abs(dx);

        if (absDx <= cfg.STOP_DIST) {
            entry.wasMovingX = false;
            return 0;
        }
        if (!entry.wasMovingX && absDx <= cfg.FOLLOW_DIST) {
            return 0; // inside dead zone — don't start until sufficiently far
        }
        entry.wasMovingX = true;
        return Math.min(absDx, walkStep(map)) * (dx >= 0 ? 1 : -1);
    }

    /**
     * Returns true if taking stepX from botPos lands on a foothold within
     * the acceptable slope range (i.e. the path is walkable, not a cliff/wall).
     */
    static boolean isPathWalkable(Character bot, Point botPos, int stepX) {
        Config cfg = BotMovementManager.cfg;
        Point next = bot.getMap().getPointBelow(new Point(botPos.x + stepX, botPos.y - cfg.MAX_SLOPE_UP));
        if (next == null) return false;
        int dy = next.y - botPos.y;
        // Walkable if we're going slightly downhill (< MAX_SNAP_DROP) or uphill (< MAX_SLOPE_UP)
        return dy <= cfg.MAX_SNAP_DROP && dy >= -cfg.MAX_SLOPE_UP;
    }

    static void initiateJump(BotEntry entry, Character bot, int dx) {
        Config cfg = BotMovementManager.cfg;
        Point p = bot.getPosition();
        entry.physX = p.x;
        entry.physY = p.y;
        stopGroundMotion(entry);
        entry.velY        = -jumpForcePerTick();
        entry.inAir       = true;
        entry.seekingRope = false;
        int jumpVelY = Math.round(-cfg.JUMP_SPEED_PXS); // px/s for movement packet
        if (dx == 0) {
            // Vertical jump — only when explicitly 0 (winDir=0 or owner directly above)
            entry.airVelX = 0;
            bot.setStance(6);
            broadcastMovement(bot, 0, jumpVelY);
        } else {
            int walkStep = walkStep(bot.getMap());
            entry.airVelX = dx >= 0 ? walkStep : -walkStep;
            bot.setStance(dx >= 0 ? 6 : 7);
            broadcastMovement(bot, velocityFromDeltaX(entry.airVelX), jumpVelY);
        }
    }

    static void initiateRopeJump(BotEntry entry, Character bot, int dx) {
        Config cfg = BotMovementManager.cfg;
        Point p = bot.getPosition();
        entry.physX = p.x;
        entry.physY = p.y;
        stopGroundMotion(entry);
        entry.velY        = -jumpForcePerTick();
        entry.inAir       = true;
        entry.seekingRope = true;
        int walkStep = walkStep(bot.getMap());
        entry.airVelX     = dx > 0 ? walkStep : dx < 0 ? -walkStep : 0;
        bot.setStance(dx >= 0 ? 6 : 7);
        int jumpVelY  = -(int) ((jumpForcePerTick() - gravityPerTick()) * (1000f / cfg.TICK_MS));
        int velXBcast = velocityFromDeltaX(entry.airVelX);
        broadcastMovement(bot, velXBcast, jumpVelY);
    }

    /**
     * Finds the nearest rope that goes above the bot's current position and is
     * within jump reach below.  No longer requires the rope to reach the owner's
     * exact Y — this allows multi-rope scenarios where the bot chains several ropes.
     */
    static Rope findNearbyRope(Character bot, Point botPos) {
        Config cfg = BotMovementManager.cfg;
        Rope best     = null;
        int  bestDist = cfg.ROPE_SEEK_X + 1;
        // Max upward reach: v0²/(2g) — how high the bot can jump to catch the rope bottom
        int jumpReach = (int) calculateMaxJumpHeight();
        for (Rope r : bot.getMap().getRopes()) {
            int dist = Math.abs(r.x() - botPos.x);
            // Rope must go above bot (topY < botPos.y) and its bottom must be reachable
            if (dist < bestDist && r.topY() < botPos.y && r.bottomY() >= botPos.y - jumpReach) {
                bestDist = dist;
                best     = r;
            }
        }
        return best;
    }

    /** Finds the nearest rope whose top is above the bot, within WAYPOINT_SEEK_X radius. */
    static Rope findWaypointRope(Character bot, Point botPos) {
        Config cfg = BotMovementManager.cfg;
        Rope best    = null;
        int  bestDist = cfg.WAYPOINT_SEEK_X + 1;
        for (Rope r : bot.getMap().getRopes()) {
            int dist = Math.abs(r.x() - botPos.x);
            if (dist < bestDist && r.topY() < botPos.y && r.bottomY() > botPos.y - calculateMaxJumpHeight()) {
                bestDist = dist;
                best     = r;
            }
        }
        return best;
    }

    /**
     * Simulates the jump arc from {@code from} stepping {@code stepX} per tick.
     * Returns true if the bot would land on a foothold meaningfully higher than
     * its current position (gaining at least JUMP_Y_THRESH px of height).
     *
     * Uses the same prevY→newY crossing pattern as tickAirborne's landing check
     * so platforms are never missed due to arc position quantisation.
     */
    static boolean arcCheckJump(Character bot, Point from, int stepX, int targetX, int targetY) {
        Config cfg = BotMovementManager.cfg;
        float  vy    = -jumpForcePerTick();
        double physY = from.y;
        int    x     = from.x;
        int    prevIntY = from.y;
        for (int t = 0; t < (1500 / cfg.TICK_MS); t++) {
            vy = Math.min(vy + gravityPerTick(), maxFallPerTick());
            x += stepX;
            physY += vy;
            int intY = (int) Math.round(physY);
            if (vy > 0) { // descending — mirrors tickAirborne landing check
                Point floor = bot.getMap().getPointBelow(new Point(x, prevIntY + 1));
                if (floor != null && floor.y <= intY && floor.y < from.y) {
                    boolean nearTargetY = Math.abs(floor.y - targetY) <= 100;
                    boolean crossedX    = (from.x < targetX && x > targetX)
                                       || (from.x > targetX && x < targetX);
                    boolean overshoot   = stepX != 0 && nearTargetY && crossedX;
                    return !overshoot;
                }
            }
            prevIntY = intY;
        }
        return false;
    }

    // ─── Movement broadcast ───────────────────────────────────────────────────

    /**
     * Broadcasts a MOVE_PLAYER packet with real velocity values so the client
     * smoothly interpolates over TICK_MS ms — matching how real player packets work.
     *
     * AbsoluteLifeMovement layout (15 bytes total):
     *   numCmds(1) cmd(1) x(2) y(2) xv(2) yv(2) fh(2) stance(1) duration(2)
     */
    static void broadcastMovement(Character bot, int velX, int velY) {
        Config cfg = BotMovementManager.cfg;
        byte[] d = new byte[15];
        d[0] = 1; // numCmds
        // d[1] = 0 = AbsoluteLifeMovement cmd (already 0)
        int x = bot.getPosition().x;
        int y = bot.getPosition().y;
        d[2]  = (byte)  (x & 0xFF);        d[3]  = (byte) (x >> 8);
        d[4]  = (byte)  (y & 0xFF);        d[5]  = (byte) (y >> 8);
        d[6]  = (byte) (velX & 0xFF);      d[7]  = (byte) (velX >> 8);
        d[8]  = (byte) (velY & 0xFF);      d[9]  = (byte) (velY >> 8);
        // d[10..11] = fh = 0 (client recalculates)
        d[12] = (byte) bot.getStance();
        d[13] = (byte) (cfg.TICK_MS & 0xFF); d[14] = (byte) (cfg.TICK_MS >> 8);
        InPacket ip  = new ByteBufInPacket(Unpooled.wrappedBuffer(d));
        Packet pkt = PacketCreator.movePlayer(bot.getId(), ip, d.length);
        bot.getMap().broadcastMessage(bot, pkt, false);
    }

    static Map<Integer, Foothold> buildFhIndex(MapleMap map) {
        Map<Integer, Foothold> index = new HashMap<>();
        for (Foothold fh : map.getFootholds().getAllFootholds()) {
            index.put(fh.getId(), fh);
        }
        return index;
    }
}
