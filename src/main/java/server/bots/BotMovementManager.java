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
    private static final int BASE_SIM_TICK_MS = 50;

    static class Config {
        // Tick
        public int   TICK_MS      = 50;   // ms between movement simulation ticks

        // Movement
        public int   STEP         = 13;    // px/tick walk step
        public int   WALK_VEL     = 133;   // px/s for client interpolation
        public int   STOP_DIST    = 30;    // stop moving within this many px
        public int   FOLLOW_DIST  = 80;    // start chasing when farther than this

        // Physics
        public float GRAVITY      = 15f;
        public float JUMP_FORCE   = 60f;
        public float JUMP_FORCE_DOWNWARD = 16f;
        public float JUMP_FORCE_ROPE     = 16f;
        public float MAX_FALL     = 50f;
        public float GROUND_ACCEL = 4.5f;
        public float GROUND_FRICTION = 3.0f;

        // Jump control
        public int   JUMP_Y_THRESH    = 30;
        public int   JUMP_COOLDOWN_MS = 1000;
        public int   ARC_LEAD_STEPS = 3;
        public int   MAX_SNAP_DROP  = 16;
        public int   MAX_SLOPE_UP   = 26;

        // Rope climbing
        public int   CLIMB_SPEED  = 10;
        public int   CLIMB_VEL    = 130;
        public int   ROPE_SEEK_X  = 150;
        public int   ROPE_GRAB_X  = 22;
        public int   TELEPORT_DIST = 2000;

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

    static int scaleDistanceForTick(int distanceAtBaseTick) {
        if (distanceAtBaseTick <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(distanceAtBaseTick * (cfg.TICK_MS / (float) BASE_SIM_TICK_MS)));
    }

    static float scaleDistanceForTick(float distanceAtBaseTick) {
        if (distanceAtBaseTick <= 0f) {
            return 0f;
        }
        return distanceAtBaseTick * (cfg.TICK_MS / (float) BASE_SIM_TICK_MS);
    }

    static int walkStep() {
        return scaleDistanceForTick(cfg.STEP);
    }

    static float gravityPerTick() {
        return scaleDistanceForTick(cfg.GRAVITY);
    }

    static float maxFallPerTick() {
        return scaleDistanceForTick(cfg.MAX_FALL);
    }

    static float jumpForcePerTick() {
        return scaleDistanceForTick(cfg.JUMP_FORCE);
    }

    static float downJumpForcePerTick() {
        return scaleDistanceForTick(cfg.JUMP_FORCE_DOWNWARD);
    }

    static float ropeJumpForcePerTick() {
        return scaleDistanceForTick(cfg.JUMP_FORCE_ROPE);
    }

    static float groundAccelPerTick() {
        return scaleDistanceForTick(cfg.GROUND_ACCEL);
    }

    static float groundFrictionPerTick() {
        return scaleDistanceForTick(cfg.GROUND_FRICTION);
    }

    static void stopGroundMotion(BotEntry entry) {
        entry.groundVelX = 0f;
        entry.groundXCarry = 0f;
    }

    static int updateGroundStep(BotEntry entry, int desiredDirection) {
        float maxSpeed = walkStep();
        float accel = groundAccelPerTick();
        float friction = groundFrictionPerTick();

        if (desiredDirection > 0) {
            entry.groundVelX = Math.min(maxSpeed, entry.groundVelX + accel);
        } else if (desiredDirection < 0) {
            entry.groundVelX = Math.max(-maxSpeed, entry.groundVelX - accel);
        } else if (entry.groundVelX > 0f) {
            entry.groundVelX = Math.max(0f, entry.groundVelX - friction);
        } else if (entry.groundVelX < 0f) {
            entry.groundVelX = Math.min(0f, entry.groundVelX + friction);
        }

        entry.groundXCarry += entry.groundVelX;
        int stepX = Math.round(entry.groundXCarry);
        entry.groundXCarry -= stepX;
        return stepX;
    }

    static void resetEntryState(BotEntry entry) {
        entry.inAir             = true;
        entry.climbing          = false;
        entry.velY              = 0f;
        entry.groundVelX        = 0f;
        entry.groundXCarry      = 0f;
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

    static void tickClimbing(BotEntry entry, Point targetPos) {
        Config cfg = BotMovementManager.cfg;
        Character bot = entry.bot;
        Point botPos  = bot.getPosition();
        int dy      = targetPos.y - botPos.y;
        int dxOwner = targetPos.x - entry.climbRope.x();

        entry.jumpCooldownMs = tickDown(entry.jumpCooldownMs);

        // Jump off rope if owner is too far horizontally and below
        if (Math.abs(dxOwner) > cfg.FOLLOW_DIST && entry.jumpCooldownMs == 0 && entry.climbRope.bottomY() < targetPos.y) {
            jumpOffRope(entry, bot, dxOwner);
            return;
        }

        // Reached top of rope — snap to foothold
        if (botPos.y <= entry.climbRope.topY()) {
            entry.climbing = false;
            Point ground = bot.getMap().getPointBelow(new Point(botPos.x, botPos.y - 3));
            if (ground != null && ground.y <= botPos.y + cfg.CLIMB_SPEED + 2) {
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

        // Reached bottom of rope — freefall
        if (botPos.y >= entry.climbRope.bottomY() + 3) {
            entry.climbing = false;
            entry.inAir    = true;
            entry.velY     = 0f;
            bot.setStance(6);
            broadcastMovement(bot, 0, 0);
            return;
        }

        // Close enough to owner — hold position on rope (follow mode only; grind always climbs to top)
        if (!entry.grinding && Math.abs(dy) < cfg.FOLLOW_DIST && Math.abs(dxOwner) < cfg.FOLLOW_DIST * 2) {
            bot.setStance(entry.climbRope.isLadder() ? 17 : 16);
            broadcastMovement(bot, 0, 0);
            return;
        }

        int newY = (dy < 0) || (entry.climbRope.topY() < botPos.y + 200) ? botPos.y - cfg.CLIMB_SPEED
                          : Math.min(botPos.y + cfg.CLIMB_SPEED, entry.climbRope.bottomY());
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
        int walkStep = walkStep();
        entry.airVelX         = dx > 0 ? walkStep : dx < 0 ? -walkStep : 0;
        entry.ropeGrabCooldownMs = delayAfterCurrentTick(cfg.JUMP_COOLDOWN_MS + 200); // stay off rope long enough to clear it
        entry.jumpCooldownMs    = delayAfterCurrentTick(cfg.JUMP_COOLDOWN_MS);
        bot.setStance(dx >= 0 ? 6 : 7);
        int jumpVelY = (int) -ropeJumpForcePerTick();
        broadcastMovement(bot, dx >= 0 ? cfg.WALK_VEL : -cfg.WALK_VEL, jumpVelY);
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
                    return;
                }
            }
        }

        entry.velY = Math.min(entry.velY + gravityPerTick(), maxFallPerTick());
        int newY   = botPos.y + (int) entry.velY;

        // Landing check — search strictly below current Y to avoid immediately re-landing
        // Also dont land after down jump grace period
        // on the foothold we just left (botPos.y + 1, not botPos.y - 1)
        if (entry.velY > 0 && entry.downJumpGracePeriodMS == 0) {
            Point floorPt = bot.getMap().getPointBelow(new Point(newX, botPos.y + 1));
            if (floorPt != null && floorPt.y <= newY) {
                entry.inAir       = false;
                entry.velY        = 0f;
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

        bot.setPosition(new Point(newX, newY));
        // Use locked airVelX for stance + broadcast — not the owner direction (which can flip mid-arc)
        int velXBcast = entry.airVelX > 0 ? cfg.WALK_VEL : entry.airVelX < 0 ? -cfg.WALK_VEL : 0;
        bot.setStance(entry.airVelX >= 0 ? 6 : 7);
        int velYBcast = (int) (entry.velY * (1000f / cfg.TICK_MS));
        broadcastMovement(bot, velXBcast, velYBcast);
    }

    // -------------------------------------------------------------------------
    // Ground movement with foothold awareness
    // -------------------------------------------------------------------------

    static void tickGrounded(BotEntry entry, Point targetPos) {
        Config cfg = BotMovementManager.cfg;
        Character bot = entry.bot;
        Point botPos  = bot.getPosition();
        int dx = targetPos.x - botPos.x;
        int dy = targetPos.y - botPos.y; // negative = owner is higher on screen

        Foothold currentFh = bot.getMap().getFootholds()
                .findBelow(new Point(botPos.x, botPos.y - cfg.MAX_SLOPE_UP));
        if (currentFh == null) {
            entry.inAir = true;
            entry.velY  = 0f;
            stopGroundMotion(entry);
            return;
        }

        entry.jumpCooldownMs = tickDown(entry.jumpCooldownMs);

        // Stuck detection — check every STUCK_CHECK_INTERVAL ticks whether we've moved enough
        if (entry.lastStuckCheckPos == null) entry.lastStuckCheckPos = botPos;
        entry.stuckCheckElapsedMs += cfg.TICK_MS;
        if (entry.stuckCheckElapsedMs >= cfg.STUCK_CHECK_INTERVAL_MS) {
            int moved = Math.abs(botPos.x - entry.lastStuckCheckPos.x)
                    + Math.abs(botPos.y - entry.lastStuckCheckPos.y);
            // Only flag stuck if we haven't moved AND owner is still far away
            if (moved < cfg.STUCK_MIN_MOVE && Math.abs(dx) > cfg.FOLLOW_DIST) {
                entry.rawChaseMs = cfg.STUCK_CHASE_MS;
            }
            entry.stuckCheckElapsedMs = 0;
            entry.lastStuckCheckPos = botPos;
        }
        entry.rawChaseMs = tickDown(entry.rawChaseMs);

        // Waypoint navigation — walk to a rope that was outside normal detection range
        if (entry.waypointRope != null) {
            if (dy >= -cfg.JUMP_Y_THRESH * 2) {
                entry.waypointRope = null; // owner no longer far above — situation resolved
            } else {
                entry.waypointTimerMs = tickDown(entry.waypointTimerMs);
                if (entry.waypointTimerMs == 0) {
                    entry.waypointRope = null; // timed out
                } else {
                    Rope wp  = entry.waypointRope;
                int  wdx = wp.x() - botPos.x;
                if (Math.abs(wdx) < cfg.ROPE_GRAB_X) {
                    // Reached the rope — grab directly or jump to it
                    entry.waypointRope = null;
                    if (botPos.y >= wp.topY() && botPos.y <= wp.bottomY()) {
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
                // Walk one step toward the rope X
                int stepToWp = Math.min(Math.abs(wdx), walkStep()) * (wdx >= 0 ? 1 : -1);
                int walkX    = botPos.x + stepToWp;
                Point snapped = bot.getMap().getPointBelow(new Point(walkX, botPos.y - cfg.MAX_SLOPE_UP));
                if (snapped != null && snapped.y <= botPos.y + cfg.MAX_SNAP_DROP) {
                    bot.setPosition(new Point(walkX, snapped.y));
                    bot.setStance(wdx >= 0 ? 2 : 3);
                    broadcastMovement(bot, wdx >= 0 ? cfg.WALK_VEL : -cfg.WALK_VEL, 0);
                    return;
                }
                // Path blocked — redirect targetPos to waypoint rope so the proactive jump section handles it
                targetPos = new Point(wp.x(), wp.topY());
                // fall through to proactive jump below
                }
            }
        }

        // Complete a pending down-jump (prone was shown last tick — now execute the fall)
        if (entry.downJumpPending) {
            entry.downJumpPending = false;
            entry.downJumpGracePeriodMS = 300L;
            entry.inAir           = true;
            entry.velY            = -downJumpForcePerTick(); // slight upward force before gravity pulls through floor
            entry.airVelX         = 0;
            entry.jumpCooldownMs  = delayAfterCurrentTick(cfg.JUMP_COOLDOWN_MS);
            bot.setPosition(new Point(botPos.x, botPos.y));
            bot.setStance(dx >= 0 ? 6 : 7);
            broadcastMovement(bot, 0, (int) entry.velY);
            return;
        }

        // Down-jump: owner is clearly below AND primarily below (not just diagonal separation)
        if (dy > cfg.JUMP_Y_THRESH * 3 && dy > Math.abs(dx) && entry.jumpCooldownMs == 0) {
            // Prefer walking off a natural terrain drop — sample position-only, no foothold connectivity
            if (Math.abs(dx) > cfg.STOP_DIST) {
                int sampleDir = dx > 0 ? walkStep() : -walkStep();
                int dropStepX = 0;
                for (int i = 1; i * walkStep() <= cfg.LEDGE_SEEK_X; i++) {
                    Point sp = bot.getMap().getPointBelow(
                            new Point(botPos.x + sampleDir * i, botPos.y - cfg.MAX_SLOPE_UP));
                    if (sp == null || sp.y > botPos.y + cfg.MAX_SNAP_DROP) {
                        dropStepX = sampleDir;
                        break;
                    }
                }
                if (dropStepX != 0) {
                    // Walk one step toward the drop (or fall off if already at the edge)
                    int walkX = botPos.x + dropStepX;
                    Point walkSnapped = bot.getMap().getPointBelow(
                            new Point(walkX, botPos.y - cfg.MAX_SLOPE_UP));
                    if (walkSnapped != null && walkSnapped.y <= botPos.y + cfg.MAX_SNAP_DROP) {
                        bot.setPosition(new Point(walkX, walkSnapped.y));
                        bot.setStance(dropStepX > 0 ? 2 : 3);
                        broadcastMovement(bot, dropStepX > 0 ? cfg.WALK_VEL : -cfg.WALK_VEL, 0);
                    } else {
                        entry.inAir   = true;
                        stopGroundMotion(entry);
                        entry.airVelX = dropStepX;
                        entry.velY    = 0f;
                    }
                    return;
                }
            }
            // No nearby drop (or owner directly below) — use prone down-jump
            entry.downJumpPending = true;
            bot.setStance(cfg.PRONE_STANCE);
            broadcastMovement(bot, 0, 0);
            return;
        }

        // Rope check — only when owner is primarily above, not just sideways (mirrors down-jump guard)
        if (dy < -cfg.JUMP_Y_THRESH * 2 && Math.abs(dy) >= Math.abs(dx)) {
            Rope rope = findNearbyRope(bot, botPos);
            if (rope != null) {
                int rdx = rope.x() - botPos.x;
                // Already within rope extent — climb directly
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
                // Prefer a direct platform jump over walking to a distant rope.
                // If any arc (toward target, away, or vertical) lands on a higher platform, skip rope.
                if (entry.jumpCooldownMs == 0) {
                    int arcStep = dx >= 0 ? walkStep() : -walkStep();
                    boolean jumpWorks = arcCheckJump(bot, botPos, arcStep, targetPos.x, targetPos.y)
                            || arcCheckJump(bot, botPos, -arcStep, targetPos.x, targetPos.y)
                            || arcCheckJump(bot, botPos, 0, targetPos.x, targetPos.y);
                    if (jumpWorks) {
                        // Fall through to proactive jump section which will handle it
                    } else {
                        // No jumpable platform — commit to rope approach
                        int maxHTravel = walkStep() * (int) (2 * jumpForcePerTick() / gravityPerTick());
                        if (Math.abs(rdx) <= maxHTravel) {
                            entry.jumpCooldownMs = delayAfterCurrentTick(cfg.JUMP_COOLDOWN_MS);
                            initiateRopeJump(entry, bot, rdx);
                            return;
                        }
                        // Rope too far for a direct jump — walk toward it
                        int stepToRope = Math.min(Math.abs(rdx), walkStep()) * (rdx >= 0 ? 1 : -1);
                        int newX = botPos.x + stepToRope;
                        Point snapped = bot.getMap().getPointBelow(new Point(newX, botPos.y - cfg.MAX_SLOPE_UP));
                        if (snapped != null && snapped.y <= botPos.y + cfg.MAX_SNAP_DROP) {
                            bot.setPosition(new Point(newX, snapped.y));
                            bot.setStance(rdx >= 0 ? 2 : 3);
                            broadcastMovement(bot, rdx >= 0 ? cfg.WALK_VEL : -cfg.WALK_VEL, 0);
                            return;
                        }
                    }
                } else {
                    // On jump cooldown — walk toward rope X to align horizontally
                    int stepToRope = Math.min(Math.abs(rdx), walkStep()) * (rdx >= 0 ? 1 : -1);
                    int newX = botPos.x + stepToRope;
                    Point snapped = bot.getMap().getPointBelow(new Point(newX, botPos.y - cfg.MAX_SLOPE_UP));
                    if (snapped != null && snapped.y <= botPos.y + cfg.MAX_SNAP_DROP) {
                        bot.setPosition(new Point(newX, snapped.y));
                        bot.setStance(rdx >= 0 ? 2 : 3);
                        broadcastMovement(bot, rdx >= 0 ? cfg.WALK_VEL : -cfg.WALK_VEL, 0);
                        return;
                    }
                }
            }
        }

        // Proactive jump — path blocked, same X, or owner is significantly above (y-chase priority)
        if (dy < -cfg.JUMP_Y_THRESH && entry.jumpCooldownMs == 0) {
            int stepX    = calcStepX(entry, botPos.x, targetPos.x);
            boolean blocked  = stepX == 0 || !isPathWalkable(bot, botPos, stepX);
            boolean farAbove = dy < -cfg.JUMP_Y_THRESH * 2; // owner is clearly on a higher platform
            if (blocked) {
                int arcStep  = stepX != 0 ? stepX : (dx >= 0 ? walkStep() : -walkStep());
                int maxJumpH = (int) calculateMaxJumpHeight();
                // Track the winning jump direction so initiateJump uses the correct airVelX
                int winDir = Integer.MIN_VALUE; // sentinel: no winner yet
                if (arcCheckJump(bot, botPos, arcStep, targetPos.x, targetPos.y)) {
                    winDir = arcStep;                       // diagonal arc found a platform
                } else if (arcCheckJump(bot, botPos, 0, targetPos.x, targetPos.y)) {
                    winDir = 0;                             // vertical arc found a platform
                } else if (farAbove) {
                    // Widen search: walk 1..ARC_LEAD_STEPS in either direction then check both jump dirs
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
                // Stuck recovery: try jumping backward if there's a platform behind us that's higher
                if (entry.rawChaseMs > 0) {
                    int backStep = -arcStep;
                    if (Math.abs(dx) <= cfg.STUCK_WALKBACK_LIMIT
                            && arcCheckJump(bot, botPos, backStep, targetPos.x, targetPos.y)) {
                        entry.jumpCooldownMs = delayAfterCurrentTick(cfg.JUMP_COOLDOWN_MS);
                        initiateJump(entry, bot, backStep);
                        return;
                    }
                }
                // No reachable platform — try setting a waypoint to a rope outside normal range.
                // Only trigger when target is significantly above (WAYPOINT_MIN_DY), not just farAbove.
                if (farAbove && -dy >= cfg.WAYPOINT_MIN_DY && entry.waypointRope == null && entry.jumpCooldownMs == 0) {
                    Rope wp = findWaypointRope(bot, botPos);
                    if (wp != null) {
                        entry.waypointRope  = wp;
                        entry.waypointTimerMs = delayAfterCurrentTick(cfg.WAYPOINT_TIMEOUT_MS);
                    }
                }
                entry.jumpCooldownMs = entry.rawChaseMs > 0
                        ? delayAfterCurrentTick(cfg.JUMP_COOLDOWN_MS)
                        : delayAfterCurrentTick(cfg.JUMP_COOLDOWN_MS * 3);
            }
        }

        // Close enough — stand still (STOP_DIST < FOLLOW_DIST gives a dead zone)
        boolean closeEnough = Math.abs(dx) < cfg.STOP_DIST && Math.abs(dy) < cfg.STOP_DIST;
        int desiredDirection;
        if (closeEnough) {
            entry.wasMovingX = false;
            desiredDirection = 0;
        } else {
            int desiredStepX = calcStepX(entry, botPos.x, targetPos.x);
            desiredDirection = Integer.compare(desiredStepX, 0);
        }
        int stepX = updateGroundStep(entry, desiredDirection);
        if (closeEnough && stepX == 0) {
            bot.setStance(5);
            broadcastMovement(bot, 0, 0);
            return;
        }
        if (desiredDirection != 0 && stepX == 0) {
            bot.setStance(desiredDirection > 0 ? 2 : 3);
            broadcastMovement(bot, desiredDirection > 0 ? cfg.WALK_VEL : -cfg.WALK_VEL, 0);
            return;
        }
        int newX  = botPos.x + stepX;

        // Normal ground walk — snap Y to terrain at newX
        // Search from MAX_SLOPE_UP above current Y to handle uphill slopes
        Point snapped = bot.getMap().getPointBelow(new Point(newX, botPos.y - cfg.MAX_SLOPE_UP));
        if (snapped == null || snapped.y > botPos.y + cfg.MAX_SNAP_DROP) {
            // About to walk off a ledge
            if (dy > cfg.JUMP_Y_THRESH) {
                // Owner is below — intentional descent, fall through
                entry.inAir  = true;
                stopGroundMotion(entry);
                entry.airVelX = stepX;
                entry.velY   = 0f;
            } else if (entry.jumpCooldownMs == 0 && stepX != 0
                    && arcCheckJump(bot, botPos, stepX, targetPos.x, targetPos.y)) {
                // Arc would reach a useful platform — jump instead of falling
                initiateJump(entry, bot, dx);
            } else {
                // No valid landing — abort step, stand at edge
                stopGroundMotion(entry);
                bot.setStance(5);
                broadcastMovement(bot, 0, 0);
            }
            return;
        }

        int velX;
        int newStance;
        if (stepX > 0)      { newStance = 2; velX =  cfg.WALK_VEL; }
        else if (stepX < 0) { newStance = 3; velX = -cfg.WALK_VEL; }
        else                 { newStance = 5; velX = 0; }

        bot.setPosition(new Point(newX, snapped.y));
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
    static int calcStepX(BotEntry entry, int botX, int targetX) {
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
        return Math.min(absDx, walkStep()) * (dx >= 0 ? 1 : -1);
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
        stopGroundMotion(entry);
        entry.velY        = -jumpForcePerTick();
        entry.inAir       = true;
        entry.seekingRope = false;
        int jumpVelY = (int) -jumpForcePerTick();
        if (dx == 0) {
            // Vertical jump — only when explicitly 0 (winDir=0 or owner directly above)
            entry.airVelX = 0;
            bot.setStance(6);
            broadcastMovement(bot, 0, jumpVelY);
        } else {
            int walkStep = walkStep();
            entry.airVelX = dx >= 0 ? walkStep : -walkStep;
            bot.setStance(dx >= 0 ? 6 : 7);
            broadcastMovement(bot, dx >= 0 ? cfg.WALK_VEL : -cfg.WALK_VEL, jumpVelY);
        }
    }

    static void initiateRopeJump(BotEntry entry, Character bot, int dx) {
        Config cfg = BotMovementManager.cfg;
        stopGroundMotion(entry);
        entry.velY        = -jumpForcePerTick();
        entry.inAir       = true;
        entry.seekingRope = true;
        int walkStep = walkStep();
        entry.airVelX     = dx > 0 ? walkStep : dx < 0 ? -walkStep : 0;
        bot.setStance(dx >= 0 ? 6 : 7);
        int jumpVelY  = -(int) ((jumpForcePerTick() - gravityPerTick()) * (1000f / cfg.TICK_MS));
        int velXBcast = dx > 0 ? cfg.WALK_VEL : dx < 0 ? -cfg.WALK_VEL : 0;
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
        float vy = -jumpForcePerTick();
        int x = from.x, y = from.y;
        for (int t = 0; t < 40; t++) {
            int prevY = y;
            vy = Math.min(vy + gravityPerTick(), maxFallPerTick());
            x += stepX;
            y += (int) vy;
            if (vy > 0) { // descending — use prevY as search origin (mirrors tickAirborne)
                Point floor = bot.getMap().getPointBelow(new Point(x, prevY));
                if (floor != null && floor.y <= y && floor.y < from.y) {
                    // Overshoot: only reject if landing is near targetY AND bot crosses over targetX.
                    // Backward jumps that gain Y (landing far from targetY) are always allowed.
                    boolean nearTargetY  = Math.abs(floor.y - targetY) <= 100;
                    boolean crossedX     = (from.x < targetX && x > targetX)
                                        || (from.x > targetX && x < targetX);
                    boolean overshoot    = stepX != 0 && nearTargetY && crossedX;
                    return !overshoot;
                }
            }
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
