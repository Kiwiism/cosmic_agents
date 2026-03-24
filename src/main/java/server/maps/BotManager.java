package server.maps;

import client.Character;
import io.netty.buffer.Unpooled;
import net.packet.ByteBufInPacket;
import net.packet.InPacket;
import net.packet.Packet;
import net.server.Server;
import net.server.channel.handlers.AbstractDealDamageHandler;
import net.server.channel.handlers.CloseRangeDamageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.TimerManager;
import server.life.Monster;
import tools.PacketCreator;

import java.awt.Point;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public class BotManager {
    private static final Logger log = LoggerFactory.getLogger(BotManager.class);
    private static final BotManager instance = new BotManager();

    /**
     * All tunable constants in one place. Fields are non-final so the class can
     * be hotswapped in debug mode without the JVM inlining the values.
     */
    public static class Config {
        // Movement
        public int   TICK_MS      = 100;   // ms between ticks (matches real v83 ~100ms packet rate)
        public int   STEP         = 13;    // px/tick walk step (133px/s * 0.1s)
        public int   WALK_VEL     = 133;   // px/s written into xv for client interpolation
        public int   STOP_DIST    = 30;    // stop moving when within this many px
        public int   FOLLOW_DIST  = 80;    // only start chasing when farther than this (hysteresis)

        // Physics
        public float GRAVITY      = 15f;   // px/tick² (downward acceleration)
        public float JUMP_FORCE   = 60f;   // initial upward velocity px/tick
        public float JUMP_FORCE_DOWNWARD   = 16f;   // initial upward velocity px/tick
        public float JUMP_FORCE_ROPE   = 16f;   // initial upward velocity px/tick
        public float MAX_FALL     = 50f;   // terminal fall velocity px/tick

        // Jump control
        public int   JUMP_Y_THRESH = 30;   // jump when target is this many px higher
        public int   JUMP_COOLDOWN = 10;   // ticks between jump attempts (~1s at 100ms)
        public int   ARC_LEAD_STEPS = 3;  // extra arc checks from 1–N steps ahead (widens jump detection)
        public int   MAX_SNAP_DROP = 16;   // px downward before going airborne (covers 45° with STEP=13)
        public int   MAX_SLOPE_UP  = 26;   // px of upward rise per step considered a walkable slope

        // Rope climbing
        public int   CLIMB_SPEED  = 10;    // px/tick upward (~51px per 510ms observed in real packets)
        public int   CLIMB_VEL    = 130;   // unused for broadcast (real packets use xv=0 yv=0 on rope)
        public int   ROPE_SEEK_X  = 150;   // horizontal search radius for ropes
        public int   ROPE_GRAB_X   = 22;    // max X distance to grab/start climbing a rope
        public int   TELEPORT_DIST = 2000; // Manhattan distance before bot teleports to owner
        public int   PRONE_STANCE  = 10;   // stance before down-jump: confirmed state=10 = down-held/crouch on ground
        public int   LEDGE_SEEK_X  = 150; // px; if foothold edge is within this radius, walk off it instead of down-jumping

        // Stuck recovery
        public int   STUCK_CHECK_INTERVAL = 30;  // ticks between stuck-position checks
        public int   STUCK_CHASE_TICKS    = 60;  // ticks of raw-chase mode after stuck detected
        public int   STUCK_MIN_MOVE       = 20;  // px; moved less than this in N ticks = stuck
        public int   STUCK_WALKBACK_LIMIT = 200; // px; max backward travel allowed during raw-chase

        // Waypoint (1-hop pathfinding to a rope outside normal detection range)
        public int   WAYPOINT_SEEK_X  = 1500;  // expanded rope search radius when setting a waypoint
        public int   WAYPOINT_TIMEOUT = 80;   // ticks before an unreached waypoint expires (~8s)

        // Grind mode
        public int   ATTACK_RANGE_SQ  = 22500; // px² — attack when within ~150px of target monster
        public int   ATTACK_COOLDOWN  = 8;     // ticks between attacks (~800ms at 100ms/tick)
        public int   GRIND_SEEK_RANGE = 800;   // px; monster search radius
    }

    /** Singleton config — replace with `cfg = new Config()` after hotswapping to reset. */
    public static Config cfg = new Config();

    public static BotManager getInstance() { return instance; }

    private final Map<Integer, BotEntry> bots = new ConcurrentHashMap<>();

    private static final Pattern FOLLOW_PATTERN = Pattern.compile(
            "\\b(follow( me)?|come( here)?|f me)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern STOP_PATTERN = Pattern.compile(
            "\\b(stop|stay|wait|halt|hold)\\b", Pattern.CASE_INSENSITIVE);

    private static final List<String> FOLLOW_REPLIES = List.of(
            "ok", "k", "sure", "omw", "got it", "coming",
            "roger", "yep", "alright",
            "aye", "lets go!", "as you wish", "ok boss");
    private static final List<String> STOP_REPLIES = List.of(
            "ok", "k", "sure", "alright", "got it", "stopping",
            "ok ill wait here", "ill be here", "np", "standing by",
            "understood", "ok boss");
    private static final Pattern GRIND_PATTERN = Pattern.compile(
            "\\b(farm|grind|kill mobs?)\\b", Pattern.CASE_INSENSITIVE);
    private static final List<String> GRIND_REPLIES = List.of(
            "ok", "on it", "lets get it", "farming time", "got it",
            "sure", "ok boss", "time to grind");

    private static String randomReply(List<String> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    private void botSay(Character bot, String text) {
        bot.getMap().broadcastMessage(PacketCreator.getChatText(bot.getId(), text, false, 0));
    }

    // -------------------------------------------------------------------------
    // Entry state
    // -------------------------------------------------------------------------

    private static class BotEntry {
        final Character bot;
        volatile boolean following = false;
        final ScheduledFuture<?> task;

        // Physics
        float velY      = 0f;
        boolean inAir   = false;
        int jumpCooldown = 0;

        // Rope climbing
        boolean climbing  = false;
        Rope    climbRope = null;

        // Jitter prevention — only starts moving toward owner once distance exceeds FOLLOW_DIST
        boolean wasMovingX = false;

        // Committed horizontal step while airborne — set at jump time, not re-computed each tick
        int airVelX = 0;

        // Rope state
        boolean seekingRope     = false; // only grab a rope mid-air when we intentionally jumped for one
        int     ropeGrabCooldown = 0;    // ticks before rope-grab is re-enabled after leaving a rope

        // Down-jump: true when prone was shown last tick, jump fires this tick
        boolean downJumpPending = false;
        long downJumpGracePeriodMS = 0;

        // Stuck recovery
        int   stuckCheckTimer     = 0;
        Point lastStuckCheckPos   = null;
        int   rawChaseTicks       = 0;

        // Waypoint — navigate to a rope outside normal detection range
        Rope  waypointRope  = null;
        int   waypointTimer = 0;

        // Grind mode
        boolean grinding       = false;
        Monster grindTarget    = null;
        int     attackCooldown = 0;

        // Foothold index, rebuilt on map change
        int lastMapId = -1;
        Map<Integer, Foothold> fhIndex = new HashMap<>();

        BotEntry(Character bot, ScheduledFuture<?> task) {
            this.bot = bot;
            this.task = task;
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void registerBot(int ownerCharId, Character bot) {
        BotEntry old = bots.remove(ownerCharId);
        if (old != null) old.task.cancel(false);

        ScheduledFuture<?> task = TimerManager.getInstance().register(
                () -> tick(ownerCharId, bot), cfg.TICK_MS);
        bots.put(ownerCharId, new BotEntry(bot, task));
    }

    public void removeBot(int ownerCharId) {
        BotEntry entry = bots.remove(ownerCharId);
        if (entry != null) entry.task.cancel(false);
    }

    /** Cancel and remove a bot by the bot character's own ID (used during shutdown). */
    public void removeBotByCharId(int botCharId) {
        bots.entrySet().removeIf(e -> {
            if (e.getValue().bot.getId() == botCharId) {
                e.getValue().task.cancel(false);
                return true;
            }
            return false;
        });
    }

    public Character getBot(int ownerCharId) {
        BotEntry entry = bots.get(ownerCharId);
        return entry != null ? entry.bot : null;
    }

    public void handleChat(Character owner, String message) {
        BotEntry entry = bots.get(owner.getId());
        if (entry == null) return;

        if (FOLLOW_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
                entry.grinding = false;
                botSay(entry.bot, randomReply(FOLLOW_REPLIES));
                TimerManager.getInstance().schedule(() -> entry.following = true, 250);
            }, 1500);
        } else if (GRIND_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
                entry.following = false;
                botSay(entry.bot, randomReply(GRIND_REPLIES));
                TimerManager.getInstance().schedule(() -> entry.grinding = true, 250);
            }, 1500);
        } else if (STOP_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
                entry.following = false;
                entry.grinding  = false;
                TimerManager.getInstance().schedule(() -> botSay(entry.bot, randomReply(STOP_REPLIES)), 1500);
            }, 1000);
        }
    }

    // -------------------------------------------------------------------------
    // Main tick
    // -------------------------------------------------------------------------

    private void tick(int ownerCharId, Character bot) {
        BotEntry entry = bots.get(ownerCharId);
        if (entry == null) return;

        // TODO: put owner in entry?
        Character owner = Server.getInstance()
                .getWorld(bot.getWorld())
                .getPlayerStorage()
                .getCharacterById(ownerCharId);
        if (owner == null) {
            entry.following = false;
            return;
        }
        Point botPos    = bot.getPosition();
        Point targetPos = owner.getPosition();

        // Keep physics running when idle; grinding runs its own navigation loop
        if (!entry.following && !entry.grinding) {
            if (entry.inAir) {
                tickAirborne(entry, targetPos);
            } else if (!entry.climbing) {
                // On ground — snap to stand stance once so walking/jumping animation clears
                if (bot.getStance() != 5) {
                    bot.setStance(5);
                    broadcastMovement(bot, 0, 0);
                }
            }
            // If climbing, bot idles on the rope — no stance change needed (16/17 is already idle)
            return;
        }

        // Map change and teleport checks only apply when following owner
        if (entry.following) {
            if (bot.getMapId() != owner.getMapId()) {
                Point spawn = new Point(owner.getPosition().x, owner.getPosition().y - 10);
                bot.setStance(5); // ensure spawn packet shows stand stance, not walk
                bot.changeMap(owner.getMap(), spawn);
                resetEntryState(entry);
                return;
            }
            // Teleport if hopelessly far (portal shortcut, fell off map, etc.)
            if (Math.abs(botPos.x - targetPos.x) + Math.abs(botPos.y - targetPos.y) > cfg.TELEPORT_DIST) {
                Point spawn = new Point(targetPos.x, targetPos.y - 10);
                bot.setPosition(spawn);
                resetEntryState(entry);
                broadcastMovement(bot, 0, 0);
                return;
            }
        }

        // Rebuild foothold index on map change
        if (entry.lastMapId != bot.getMapId()) {
            entry.fhIndex  = buildFhIndex(bot.getMap());
            entry.lastMapId = bot.getMapId();
        }

        // Grind mode: navigate toward nearest monster, attack when in range
        if (entry.grinding) {
            Monster target = findNearestMonster(bot);
            if (target == null) {
                log.debug("[GRIND] no target found for bot={} map={}", bot.getName(), bot.getMapId());
                if (entry.inAir) tickAirborne(entry, targetPos);
                else { bot.setStance(5); broadcastMovement(bot, 0, 0); }
                return;
            }
            entry.grindTarget = target;
            int distSq = (int) target.getPosition().distanceSq(botPos);
            log.debug("[GRIND] target={} distSq={} rangeSq={} inAir={} climbing={}",
                    target.getId(), distSq, cfg.ATTACK_RANGE_SQ, entry.inAir, entry.climbing);
            if (!entry.inAir && !entry.climbing
                    && distSq <= cfg.ATTACK_RANGE_SQ) {
                attackMonster(entry, bot, target);
                return;
            }
            targetPos = target.getPosition();
        }

        if (entry.climbing) {
            tickClimbing(entry, targetPos);
        } else if (entry.inAir) {
            tickAirborne(entry, targetPos);
        } else {
            tickGrounded(entry, targetPos);
        }
    }

    private void resetEntryState(BotEntry entry) {
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

    private void tickClimbing(BotEntry entry, Point targetPos) {
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

        // Close enough to owner — hold position on rope
        if (Math.abs(dy) < cfg.FOLLOW_DIST && Math.abs(dxOwner) < cfg.FOLLOW_DIST * 2) {
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
    private void jumpOffRope(BotEntry entry, Character bot, int dx) {
        entry.climbing        = false;
        entry.inAir           = true;
        entry.velY            = -cfg.JUMP_FORCE_ROPE;
        entry.seekingRope     = false;
        entry.airVelX         = dx > 0 ? cfg.STEP : dx < 0 ? -cfg.STEP : 0;
        entry.ropeGrabCooldown = cfg.JUMP_COOLDOWN + 2; // stay off rope long enough to clear it
        entry.jumpCooldown    = cfg.JUMP_COOLDOWN;
        bot.setStance(dx >= 0 ? 6 : 7);
        int jumpVelY = (int) -cfg.JUMP_FORCE_ROPE;
        broadcastMovement(bot, dx >= 0 ? cfg.WALK_VEL : -cfg.WALK_VEL, jumpVelY);
    }

    // -------------------------------------------------------------------------
    // Airborne physics
    // -------------------------------------------------------------------------

    private void tickAirborne(BotEntry entry, Point targetPos) {
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
                    broadcastMovement(bot, 0, 0);
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

    private void tickGrounded(BotEntry entry, Point targetPos) {
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
                        broadcastMovement(bot, 0, 0);
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
                    broadcastMovement(bot, wdx >= 0 ? cfg.WALK_VEL : -cfg.WALK_VEL, 0);
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
            broadcastMovement(bot, 0, (int) entry.velY);
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
                        broadcastMovement(bot, dropStepX > 0 ? cfg.WALK_VEL : -cfg.WALK_VEL, 0);
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
                    entry.climbing  = true;
                    entry.climbRope = rope;
                    bot.setPosition(new Point(rope.x(), botPos.y));
                    bot.setStance(entry.climbRope.isLadder() ? 17 : 16);
                    broadcastMovement(bot, 0, 0);
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
                    broadcastMovement(bot, rdx >= 0 ? cfg.WALK_VEL : -cfg.WALK_VEL, 0);
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
                if (arcCheckJump(bot, botPos, arcStep, targetPos.y)) {
                    winDir = arcStep;                       // diagonal arc found a platform
                } else if (arcCheckJump(bot, botPos, 0, targetPos.y)) {
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
                                if (arcCheckJump(bot, leadPt, arcStep * jDir, targetPos.y)) {
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
                            && arcCheckJump(bot, botPos, backStep, targetPos.y)) {
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
            broadcastMovement(bot, 0, 0);
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
                    && arcCheckJump(bot, botPos, stepX, targetPos.y)) {
                // Arc would reach a useful platform — jump instead of falling
                initiateJump(entry, bot, dx);
            } else {
                // No valid landing — abort step, stand at edge
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

    private float calculateMaxJumpHeight() {
        return cfg.JUMP_FORCE * cfg.JUMP_FORCE / (2 * cfg.GRAVITY);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the X step toward targetX, with hysteresis to prevent jitter:
     * only starts moving once distance exceeds FOLLOW_DIST, stops at STOP_DIST.
     */
    private int calcStepX(BotEntry entry, int botX, int targetX) {
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
    private boolean isPathWalkable(Character bot, Point botPos, int stepX) {
        Point next = bot.getMap().getPointBelow(new Point(botPos.x + stepX, botPos.y - cfg.MAX_SLOPE_UP));
        if (next == null) return false;
        int dy = next.y - botPos.y;
        // Walkable if we're going slightly downhill (< MAX_SNAP_DROP) or uphill (< MAX_SLOPE_UP)
        return dy <= cfg.MAX_SNAP_DROP && dy >= -cfg.MAX_SLOPE_UP;
    }

    private void initiateJump(BotEntry entry, Character bot, int dx) {
        entry.velY        = -cfg.JUMP_FORCE;
        entry.inAir       = true;
        entry.seekingRope = false;
        int jumpVelY = (int) -cfg.JUMP_FORCE;
        if (dx == 0) {
            // Vertical jump — only when explicitly 0 (winDir=0 or owner directly above)
            entry.airVelX = 0;
            bot.setStance(6);
            broadcastMovement(bot, 0, jumpVelY);
        } else {
            entry.airVelX = dx >= 0 ? cfg.STEP : -cfg.STEP;
            bot.setStance(dx >= 0 ? 6 : 7);
            broadcastMovement(bot, dx >= 0 ? cfg.WALK_VEL : -cfg.WALK_VEL, jumpVelY);
        }
    }

    private void initiateRopeJump(BotEntry entry, Character bot, int dx) {
        entry.velY        = -cfg.JUMP_FORCE;
        entry.inAir       = true;
        entry.seekingRope = true;
        entry.airVelX     = dx > 0 ? cfg.STEP : dx < 0 ? -cfg.STEP : 0;
        bot.setStance(dx >= 0 ? 6 : 7);
        int jumpVelY  = -(int) ((cfg.JUMP_FORCE - cfg.GRAVITY) * (1000f / cfg.TICK_MS));
        int velXBcast = dx > 0 ? cfg.WALK_VEL : dx < 0 ? -cfg.WALK_VEL : 0;
        broadcastMovement(bot, velXBcast, jumpVelY);
    }

    /**
     * Finds the nearest rope that goes above the bot's current position and is
     * within jump reach below.  No longer requires the rope to reach the owner's
     * exact Y — this allows multi-rope scenarios where the bot chains several ropes.
     */
    private Rope findNearbyRope(Character bot, Point botPos) {
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

    // -------------------------------------------------------------------------
    // Grind mode helpers
    // -------------------------------------------------------------------------

    private Monster findNearestMonster(Character bot) {
        Point botPos = bot.getPosition();
        Monster best = null;
        double bestDist = (double) cfg.GRIND_SEEK_RANGE * cfg.GRIND_SEEK_RANGE;
        for (Monster m : bot.getMap().getAllMonsters()) {
            if (!m.isAlive()) continue;
            double d = m.getPosition().distanceSq(botPos);
            if (d < bestDist) { bestDist = d; best = m; }
        }
        return best;
    }

    private void attackMonster(BotEntry entry, Character bot, Monster target) {
        if (entry.attackCooldown > 0) {
            entry.attackCooldown--;
            return;
        }

        int damage = Math.max(1, bot.calculateMaxBaseDamage(bot.getTotalWatk()));

        AbstractDealDamageHandler.AttackInfo attack = new AbstractDealDamageHandler.AttackInfo();
        attack.skill = 0;
        attack.numAttacked = 1;
        attack.numDamage = 1;
        attack.numAttackedAndDamage = (1 << 4) | 1;
        attack.speed = 4;
        // bit 7 = facing left (0x80), matching real client attack packet encoding
        attack.stance = bot.getPosition().x > target.getPosition().x ? 0x80 : 0x00;
        attack.targets = new HashMap<>();
        attack.targets.put(target.getObjectId(),
                new AbstractDealDamageHandler.AttackTarget((short) 0, List.of(damage)));

        CloseRangeDamageHandler.applyCloseRangeEffects(attack, bot, bot.getClient());

        entry.attackCooldown = cfg.ATTACK_COOLDOWN;
    }

    /**
     * Broadcasts a MOVE_PLAYER packet with real velocity values so the client
     * smoothly interpolates over TICK_MS ms — matching how real player packets work.
     *
     * AbsoluteLifeMovement layout (15 bytes total):
     *   numCmds(1) cmd(1) x(2) y(2) xv(2) yv(2) fh(2) stance(1) duration(2)
     */
    private void broadcastMovement(Character bot, int velX, int velY) {
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
        Packet   pkt = PacketCreator.movePlayer(bot.getId(), ip, d.length);
        bot.getMap().broadcastMessage(bot, pkt, false);
    }

    /**
     * Simulates the jump arc from {@code from} stepping {@code stepX} per tick.
     * Returns true if the bot would land on a foothold meaningfully higher than
     * its current position (gaining at least JUMP_Y_THRESH px of height).
     *
     * Uses the same prevY→newY crossing pattern as tickAirborne's landing check
     * so platforms are never missed due to arc position quantisation.
     */
    /** Finds the nearest rope whose top is above the bot, within WAYPOINT_SEEK_X radius. */
    private Rope findWaypointRope(Character bot, Point botPos) {
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

    private boolean arcCheckJump(Character bot, Point from, int stepX, int targetY) {
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
                    // assume sideway jump toward target always useful
                    if (stepX != 0) {
                        return true;
                    }
                    // Useful if we gain height OR land near target's platform (same-level gap jump)
//                    boolean usefulHorizontalJump = stepX != 0 && Math.abs(floor.y - from.y) <= cfg.JUMP_Y_THRESH;
                    boolean hasYGains = floor.y < from.y - cfg.JUMP_Y_THRESH;
                    return hasYGains;
                }
            }
        }
        return false;
    }

    private static Map<Integer, Foothold> buildFhIndex(MapleMap map) {
        Map<Integer, Foothold> index = new HashMap<>();
        for (Foothold fh : map.getFootholds().getAllFootholds()) {
            index.put(fh.getId(), fh);
        }
        return index;
    }
}
