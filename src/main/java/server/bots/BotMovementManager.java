package server.bots;

import client.Character;
import io.netty.buffer.Unpooled;
import net.packet.ByteBufInPacket;
import net.packet.InPacket;
import net.packet.Packet;
import tools.PacketCreator;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

class BotMovementManager {

    static void resetEntryState(BotEntry entry) {
        entry.inAir             = true;
        entry.climbing          = false;
        entry.velY              = 0f;
        entry.airVelX           = 0;
        entry.wasMovingX        = false;
        entry.seekingRope       = false;
        entry.ropeGrabCooldown  = 0;
        entry.downJumpPending   = false;
        entry.rawChaseTicks     = 0;
        entry.stuckCheckTimer   = 0;
        entry.lastStuckCheckPos = null;
        entry.waypointRope      = null;
        entry.grindTarget       = null;
        entry.attackCooldown    = 0;
    }

    // -------------------------------------------------------------------------
    // Rope climbing
    // -------------------------------------------------------------------------

    static void tickClimbing(BotEntry entry, Point targetPos) {
        BotManager.Config cfg = BotManager.cfg;
        Character bot = entry.bot;
        Point botPos  = bot.getPosition();
        int dy      = targetPos.y - botPos.y;
        int dxOwner = targetPos.x - entry.climbRope.x();

        if (entry.jumpCooldown > 0) entry.jumpCooldown--;

        // Jump off rope if owner is too far horizontally and below
        if (Math.abs(dxOwner) > cfg.FOLLOW_DIST && entry.jumpCooldown == 0 && entry.climbRope.bottomY() < targetPos.y) {
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
                bot.setPosition(new Point(botPos.x, ground.y));
                bot.setStance(5);
                BotManager.getInstance().broadcastMovement(bot, 0, 0);
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
            BotManager.getInstance().broadcastMovement(bot, 0, 0);
            return;
        }

        // Close enough to owner — hold position on rope (follow mode only; grind always climbs to top)
        if (!entry.grinding && Math.abs(dy) < cfg.FOLLOW_DIST && Math.abs(dxOwner) < cfg.FOLLOW_DIST * 2) {
            bot.setStance(entry.climbRope.isLadder() ? 17 : 16);
            BotManager.getInstance().broadcastMovement(bot, 0, 0);
            return;
        }

        int newY = (dy < 0) || (entry.climbRope.topY() < botPos.y + 200) ? botPos.y - cfg.CLIMB_SPEED
                          : Math.min(botPos.y + cfg.CLIMB_SPEED, entry.climbRope.bottomY());
        bot.setPosition(new Point(entry.climbRope.x(), newY));
        bot.setStance(entry.climbRope.isLadder() ? 17 : 16);
        BotManager.getInstance().broadcastMovement(bot, 0, 0);
    }

    /** Jump off the current rope position toward dx direction. */
    static void jumpOffRope(BotEntry entry, Character bot, int dx) {
        BotManager.Config cfg = BotManager.cfg;
        entry.climbing        = false;
        entry.inAir           = true;
        entry.velY            = -cfg.JUMP_FORCE_ROPE;
        entry.seekingRope     = false;
        entry.airVelX         = dx > 0 ? cfg.STEP : dx < 0 ? -cfg.STEP : 0;
        entry.ropeGrabCooldown = cfg.JUMP_COOLDOWN + 2; // stay off rope long enough to clear it
        entry.jumpCooldown    = cfg.JUMP_COOLDOWN;
        bot.setStance(dx >= 0 ? 6 : 7);
        int jumpVelY = (int) -cfg.JUMP_FORCE_ROPE;
        BotManager.getInstance().broadcastMovement(bot, dx >= 0 ? cfg.WALK_VEL : -cfg.WALK_VEL, jumpVelY);
    }

    // -------------------------------------------------------------------------
    // Airborne physics
    // -------------------------------------------------------------------------

    static void tickAirborne(BotEntry entry, Point targetPos) {
        BotManager.Config cfg = BotManager.cfg;
        if (entry.downJumpGracePeriodMS > 0L) {
            entry.downJumpGracePeriodMS = Math.max(0L, entry.downJumpGracePeriodMS - cfg.TICK_MS);
        }

        Character bot = entry.bot;
        Point botPos  = bot.getPosition();
        int newX      = botPos.x + entry.airVelX;

        // Mid-air rope catch — only when we intentionally jumped for a rope
        if (entry.ropeGrabCooldown > 0) entry.ropeGrabCooldown--;
        if (entry.seekingRope && entry.ropeGrabCooldown == 0) {
            for (Rope rope : bot.getMap().getRopes()) {
                if (Math.abs(rope.x() - botPos.x) <= cfg.ROPE_GRAB_X
                        && botPos.y >= rope.topY() && botPos.y <= rope.bottomY()) {
                    entry.climbing    = true;
                    entry.inAir       = false;
                    entry.velY        = 0f;
                    entry.seekingRope = false;
                    entry.climbRope   = rope;
                    bot.setPosition(new Point(rope.x(), botPos.y));
                    bot.setStance(entry.climbRope.isLadder() ? 17 : 16);
                    BotManager.getInstance().broadcastMovement(bot, 0, 0);
                    return;
                }
            }
        }

        entry.velY = Math.min(entry.velY + cfg.GRAVITY, cfg.MAX_FALL);
        int newY   = botPos.y + (int) entry.velY;

        // Landing check — search strictly below current Y to avoid immediately re-landing
        // Also dont land after down jump grace period
        // on the foothold we just left (botPos.y + 1, not botPos.y - 1)
        if (entry.velY > 0 && entry.downJumpGracePeriodMS == 0) {
            Point floorPt = bot.getMap().getPointBelow(new Point(newX, botPos.y + 1));
            if (floorPt != null && floorPt.y <= newY) {
                entry.inAir       = false;
                entry.velY        = 0f;
                entry.jumpCooldown = 0;
                entry.seekingRope = false;
                entry.airVelX     = 0;
                bot.setPosition(new Point(newX, floorPt.y));
                int dx = targetPos.x - botPos.x;
                int dy = targetPos.y - botPos.y;
                bot.setStance(dx >= 0 ? 2 : 3);
                BotManager.getInstance().broadcastMovement(bot, 0, 0);
                return;
            }
        }

        bot.setPosition(new Point(newX, newY));
        // Use locked airVelX for stance + broadcast — not the owner direction (which can flip mid-arc)
        int velXBcast = entry.airVelX > 0 ? cfg.WALK_VEL : entry.airVelX < 0 ? -cfg.WALK_VEL : 0;
        bot.setStance(entry.airVelX >= 0 ? 6 : 7);
        int velYBcast = (int) (entry.velY * (1000f / cfg.TICK_MS));
        BotManager.getInstance().broadcastMovement(bot, velXBcast, velYBcast);
    }

    // -------------------------------------------------------------------------
    // Ground movement with foothold awareness
    // -------------------------------------------------------------------------

    static void tickGrounded(BotEntry entry, Point targetPos) {
        BotManager.Config cfg = BotManager.cfg;
        Character bot = entry.bot;
        Point botPos  = bot.getPosition();
        int dx = targetPos.x - botPos.x;
        int dy = targetPos.y - botPos.y; // negative = owner is higher on screen

        Foothold currentFh = bot.getMap().getFootholds()
                .findBelow(new Point(botPos.x, botPos.y - cfg.MAX_SLOPE_UP));
        if (currentFh == null) {
            entry.inAir = true;
            entry.velY  = 0f;
            return;
        }

        if (entry.jumpCooldown > 0) entry.jumpCooldown--;

        // Stuck detection — check every STUCK_CHECK_INTERVAL ticks whether we've moved enough
        if (entry.lastStuckCheckPos == null) entry.lastStuckCheckPos = botPos;
        entry.stuckCheckTimer++;
        if (entry.stuckCheckTimer >= cfg.STUCK_CHECK_INTERVAL) {
            int moved = Math.abs(botPos.x - entry.lastStuckCheckPos.x)
                    + Math.abs(botPos.y - entry.lastStuckCheckPos.y);
            // Only flag stuck if we haven't moved AND owner is still far away
            if (moved < cfg.STUCK_MIN_MOVE && Math.abs(dx) > cfg.FOLLOW_DIST) {
                entry.rawChaseTicks = cfg.STUCK_CHASE_TICKS;
            }
            entry.stuckCheckTimer   = 0;
            entry.lastStuckCheckPos = botPos;
        }
        if (entry.rawChaseTicks > 0) entry.rawChaseTicks--;

        // Waypoint navigation — walk to a rope that was outside normal detection range
        if (entry.waypointRope != null) {
            if (dy >= -cfg.JUMP_Y_THRESH * 2) {
                entry.waypointRope = null; // owner no longer far above — situation resolved
            } else if (--entry.waypointTimer <= 0) {
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
                        BotManager.getInstance().broadcastMovement(bot, 0, 0);
                    } else {
                        entry.jumpCooldown = cfg.JUMP_COOLDOWN;
                        initiateRopeJump(entry, bot, wdx);
                    }
                    return;
                }
                // Walk one step toward the rope X
                int stepToWp = Math.min(Math.abs(wdx), cfg.STEP) * (wdx >= 0 ? 1 : -1);
                int walkX    = botPos.x + stepToWp;
                Point snapped = bot.getMap().getPointBelow(new Point(walkX, botPos.y - cfg.MAX_SLOPE_UP));
                if (snapped != null && snapped.y <= botPos.y + cfg.MAX_SNAP_DROP) {
                    bot.setPosition(new Point(walkX, snapped.y));
                    bot.setStance(wdx >= 0 ? 2 : 3);
                    BotManager.getInstance().broadcastMovement(bot, wdx >= 0 ? cfg.WALK_VEL : -cfg.WALK_VEL, 0);
                } else if (dy > cfg.JUMP_Y_THRESH) {
                    // Ledge on path to waypoint and owner is below — fall through naturally
                    entry.inAir   = true;
                    entry.airVelX = stepToWp;
                    entry.velY    = 0f;
                }
                return;
            }
        }

        // Complete a pending down-jump (prone was shown last tick — now execute the fall)
        if (entry.downJumpPending) {
            entry.downJumpPending = false;
            entry.downJumpGracePeriodMS = 300L;
            entry.inAir           = true;
            entry.velY            = -cfg.JUMP_FORCE_DOWNWARD; // slight upward force before gravity pulls through floor
            entry.airVelX         = 0;
            entry.jumpCooldown    = cfg.JUMP_COOLDOWN;
            bot.setPosition(new Point(botPos.x, botPos.y));
            bot.setStance(dx >= 0 ? 6 : 7);
            BotManager.getInstance().broadcastMovement(bot, 0, (int) entry.velY);
            return;
        }

        // Down-jump: owner is clearly below AND primarily below (not just diagonal separation)
        if (dy > cfg.JUMP_Y_THRESH * 3 && dy > Math.abs(dx) && entry.jumpCooldown == 0) {
            // Prefer walking off a natural terrain drop — sample position-only, no foothold connectivity
            if (Math.abs(dx) > cfg.STOP_DIST) {
                int sampleDir = dx > 0 ? cfg.STEP : -cfg.STEP;
                int dropStepX = 0;
                for (int i = 1; i * cfg.STEP <= cfg.LEDGE_SEEK_X; i++) {
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
                        BotManager.getInstance().broadcastMovement(bot, dropStepX > 0 ? cfg.WALK_VEL : -cfg.WALK_VEL, 0);
                    } else {
                        entry.inAir   = true;
                        entry.airVelX = dropStepX;
                        entry.velY    = 0f;
                    }
                    return;
                }
            }
            // No nearby drop (or owner directly below) — use prone down-jump
            entry.downJumpPending = true;
            bot.setStance(cfg.PRONE_STANCE);
            BotManager.getInstance().broadcastMovement(bot, 0, 0);
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
                    entry.climbing  = true;
                    entry.climbRope = rope;
                    bot.setPosition(new Point(rope.x(), botPos.y));
                    bot.setStance(entry.climbRope.isLadder() ? 17 : 16);
                    BotManager.getInstance().broadcastMovement(bot, 0, 0);
                    return;
                }
                // Jump toward rope — but only if the rope is within horizontal reach of the arc
                int maxHTravel = cfg.STEP * (int) (2 * cfg.JUMP_FORCE / cfg.GRAVITY);
                if (entry.jumpCooldown == 0 && Math.abs(rdx) <= maxHTravel) {
                    entry.jumpCooldown = cfg.JUMP_COOLDOWN;
                    initiateRopeJump(entry, bot, rdx);
                    return;
                }
                // On cooldown — walk toward rope X to align horizontally
                int stepToRope = Math.min(Math.abs(rdx), cfg.STEP) * (rdx >= 0 ? 1 : -1);
                int newX = botPos.x + stepToRope;
                Point snapped = bot.getMap().getPointBelow(new Point(newX, botPos.y - cfg.MAX_SLOPE_UP));
                if (snapped != null && snapped.y <= botPos.y + cfg.MAX_SNAP_DROP) {
                    bot.setPosition(new Point(newX, snapped.y));
                    bot.setStance(rdx >= 0 ? 2 : 3);
                    BotManager.getInstance().broadcastMovement(bot, rdx >= 0 ? cfg.WALK_VEL : -cfg.WALK_VEL, 0);
                    return;
                }
            }
        }

        // Proactive jump — path blocked, same X, or owner is significantly above (y-chase priority)
        if (dy < -cfg.JUMP_Y_THRESH && entry.jumpCooldown == 0) {
            int stepX    = calcStepX(entry, botPos.x, targetPos.x);
            boolean blocked  = stepX == 0 || !isPathWalkable(bot, botPos, stepX);
            boolean farAbove = dy < -cfg.JUMP_Y_THRESH * 2; // owner is clearly on a higher platform
            if (blocked) {
                int arcStep  = stepX != 0 ? stepX : (dx >= 0 ? cfg.STEP : -cfg.STEP);
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
                    entry.jumpCooldown = cfg.JUMP_COOLDOWN;
                    initiateJump(entry, bot, winDir);
                    return;
                }
                // Stuck recovery: try jumping backward if there's a platform behind us that's higher
                if (entry.rawChaseTicks > 0) {
                    int backStep = -arcStep;
                    if (Math.abs(dx) <= cfg.STUCK_WALKBACK_LIMIT
                            && arcCheckJump(bot, botPos, backStep, targetPos.x, targetPos.y)) {
                        entry.jumpCooldown = cfg.JUMP_COOLDOWN;
                        initiateJump(entry, bot, backStep);
                        return;
                    }
                }
                // No reachable platform — try setting a waypoint to a rope outside normal range
                if (farAbove && entry.waypointRope == null) {
                    Rope wp = findWaypointRope(bot, botPos);
                    if (wp != null) {
                        entry.waypointRope  = wp;
                        entry.waypointTimer = cfg.WAYPOINT_TIMEOUT;
                    }
                }
                entry.jumpCooldown = entry.rawChaseTicks > 0 ? cfg.JUMP_COOLDOWN : cfg.JUMP_COOLDOWN * 3;
            }
        }

        // Close enough — stand still (STOP_DIST < FOLLOW_DIST gives a dead zone)
        if (Math.abs(dx) < cfg.STOP_DIST && Math.abs(dy) < cfg.STOP_DIST) {
            entry.wasMovingX = false;
            bot.setStance(5);
            BotManager.getInstance().broadcastMovement(bot, 0, 0);
            return;
        }

        int stepX = calcStepX(entry, botPos.x, targetPos.x);
        int newX  = botPos.x + stepX;

        // Normal ground walk — snap Y to terrain at newX
        // Search from MAX_SLOPE_UP above current Y to handle uphill slopes
        Point snapped = bot.getMap().getPointBelow(new Point(newX, botPos.y - cfg.MAX_SLOPE_UP));
        if (snapped == null || snapped.y > botPos.y + cfg.MAX_SNAP_DROP) {
            // About to walk off a ledge
            if (dy > cfg.JUMP_Y_THRESH) {
                // Owner is below — intentional descent, fall through
                entry.inAir  = true;
                entry.airVelX = stepX;
                entry.velY   = 0f;
            } else if (entry.jumpCooldown == 0 && stepX != 0
                    && arcCheckJump(bot, botPos, stepX, targetPos.x, targetPos.y)) {
                // Arc would reach a useful platform — jump instead of falling
                initiateJump(entry, bot, dx);
            } else {
                // No valid landing — abort step, stand at edge
                bot.setStance(5);
                BotManager.getInstance().broadcastMovement(bot, 0, 0);
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
        BotManager.getInstance().broadcastMovement(bot, velX, 0);
    }

    static float calculateMaxJumpHeight() {
        BotManager.Config cfg = BotManager.cfg;
        return cfg.JUMP_FORCE * cfg.JUMP_FORCE / (2 * cfg.GRAVITY);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the X step toward targetX, with hysteresis to prevent jitter:
     * only starts moving once distance exceeds FOLLOW_DIST, stops at STOP_DIST.
     */
    static int calcStepX(BotEntry entry, int botX, int targetX) {
        BotManager.Config cfg = BotManager.cfg;
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
        return Math.min(absDx, cfg.STEP) * (dx >= 0 ? 1 : -1);
    }

    /**
     * Returns true if taking stepX from botPos lands on a foothold within
     * the acceptable slope range (i.e. the path is walkable, not a cliff/wall).
     */
    static boolean isPathWalkable(Character bot, Point botPos, int stepX) {
        BotManager.Config cfg = BotManager.cfg;
        Point next = bot.getMap().getPointBelow(new Point(botPos.x + stepX, botPos.y - cfg.MAX_SLOPE_UP));
        if (next == null) return false;
        int dy = next.y - botPos.y;
        // Walkable if we're going slightly downhill (< MAX_SNAP_DROP) or uphill (< MAX_SLOPE_UP)
        return dy <= cfg.MAX_SNAP_DROP && dy >= -cfg.MAX_SLOPE_UP;
    }

    static void initiateJump(BotEntry entry, Character bot, int dx) {
        BotManager.Config cfg = BotManager.cfg;
        entry.velY        = -cfg.JUMP_FORCE;
        entry.inAir       = true;
        entry.seekingRope = false;
        int jumpVelY = (int) -cfg.JUMP_FORCE;
        if (dx == 0) {
            // Vertical jump — only when explicitly 0 (winDir=0 or owner directly above)
            entry.airVelX = 0;
            bot.setStance(6);
            BotManager.getInstance().broadcastMovement(bot, 0, jumpVelY);
        } else {
            entry.airVelX = dx >= 0 ? cfg.STEP : -cfg.STEP;
            bot.setStance(dx >= 0 ? 6 : 7);
            BotManager.getInstance().broadcastMovement(bot, dx >= 0 ? cfg.WALK_VEL : -cfg.WALK_VEL, jumpVelY);
        }
    }

    static void initiateRopeJump(BotEntry entry, Character bot, int dx) {
        BotManager.Config cfg = BotManager.cfg;
        entry.velY        = -cfg.JUMP_FORCE;
        entry.inAir       = true;
        entry.seekingRope = true;
        entry.airVelX     = dx > 0 ? cfg.STEP : dx < 0 ? -cfg.STEP : 0;
        bot.setStance(dx >= 0 ? 6 : 7);
        int jumpVelY  = -(int) ((cfg.JUMP_FORCE - cfg.GRAVITY) * (1000f / cfg.TICK_MS));
        int velXBcast = dx > 0 ? cfg.WALK_VEL : dx < 0 ? -cfg.WALK_VEL : 0;
        BotManager.getInstance().broadcastMovement(bot, velXBcast, jumpVelY);
    }

    /**
     * Finds the nearest rope that goes above the bot's current position and is
     * within jump reach below.  No longer requires the rope to reach the owner's
     * exact Y — this allows multi-rope scenarios where the bot chains several ropes.
     */
    static Rope findNearbyRope(Character bot, Point botPos) {
        BotManager.Config cfg = BotManager.cfg;
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
        BotManager.Config cfg = BotManager.cfg;
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
        BotManager.Config cfg = BotManager.cfg;
        float vy = -cfg.JUMP_FORCE;
        int x = from.x, y = from.y;
        for (int t = 0; t < 40; t++) {
            int prevY = y;
            vy = Math.min(vy + cfg.GRAVITY, cfg.MAX_FALL);
            x += stepX;
            y += (int) vy;
            if (vy > 0) { // descending — use prevY as search origin (mirrors tickAirborne)
                Point floor = bot.getMap().getPointBelow(new Point(x, prevY));
                if (floor != null && floor.y <= y) {
                    if (stepX != 0) {
                        // Reject if the arc overshoots the target — bot would land past owner
                        boolean overshoot = (stepX > 0 && x > targetX) || (stepX < 0 && x < targetX);
                        return !overshoot;
                    }
                    return floor.y < from.y - cfg.JUMP_Y_THRESH;
                }
            }
        }
        return false;
    }

    static Map<Integer, Foothold> buildFhIndex(MapleMap map) {
        Map<Integer, Foothold> index = new HashMap<>();
        for (Foothold fh : map.getFootholds().getAllFootholds()) {
            index.put(fh.getId(), fh);
        }
        return index;
    }
}
