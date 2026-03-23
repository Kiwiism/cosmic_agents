package server.maps;

import client.Character;
import io.netty.buffer.Unpooled;
import net.packet.ByteBufInPacket;
import net.packet.InPacket;
import net.packet.Packet;
import net.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.TimerManager;
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
        public float MAX_FALL     = 50f;   // terminal fall velocity px/tick

        // Jump control
        public int   JUMP_Y_THRESH = 35;   // jump when target is this many px higher
        public int   JUMP_COOLDOWN = 10;   // ticks between jump attempts (~1s at 100ms)
        public int   MAX_SNAP_DROP = 16;   // px downward before going airborne (covers 45° with STEP=13)
        public int   MAX_SLOPE_UP  = 26;   // px of upward rise per step considered a walkable slope

        // Rope climbing
        public int   CLIMB_SPEED  = 13;    // px/tick upward
        public int   CLIMB_VEL    = 130;   // px/s for yv field
        public int   ROPE_SEEK_X  = 150;   // horizontal search radius for ropes
        public int   ROPE_GRAB_X  = 22;    // max X distance to grab/start climbing a rope
    }

    /** Singleton config — replace with `cfg = new Config()` after hotswapping to reset. */
    public static Config cfg = new Config();

    public static BotManager getInstance() { return instance; }

    private final Map<Integer, BotEntry> bots = new ConcurrentHashMap<>();

    private static final Pattern FOLLOW_PATTERN = Pattern.compile(
            "\\b(follow( me)?|come( here)?|f me)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern STOP_PATTERN = Pattern.compile(
            "\\b(stop|stay|wait|halt)\\b", Pattern.CASE_INSENSITIVE);

    private static final List<String> FOLLOW_REPLIES = List.of(
            "ok", "sure", "on my way!", "got it", "coming!",
            "roger that", "yep!", "alright", "right behind you",
            "aye aye!", "lets go!", "as you wish");
    private static final List<String> STOP_REPLIES = List.of(
            "ok", "sure", "alright", "got it", "stopping",
            "ok ill wait here", "ill be here", "np", "standing by",
            "understood", "ok boss");

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
        boolean climbing = false;
        int climbX       = 0;
        int climbTopY    = Integer.MAX_VALUE;

        // Jitter prevention — only starts moving toward owner once distance exceeds FOLLOW_DIST
        boolean wasMovingX = false;

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
                botSay(entry.bot, randomReply(FOLLOW_REPLIES));
                entry.following = true;
            }, 2000);
        } else if (STOP_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
                entry.following = false;
                TimerManager.getInstance().schedule(() ->
                        botSay(entry.bot, randomReply(STOP_REPLIES)), 2000);
            }, 1000);
        }
    }

    // -------------------------------------------------------------------------
    // Main tick
    // -------------------------------------------------------------------------

    private void tick(int ownerCharId, Character bot) {
        BotEntry entry = bots.get(ownerCharId);
        if (entry == null || !entry.following) return;

        Character owner = Server.getInstance()
                .getWorld(bot.getWorld())
                .getPlayerStorage()
                .getCharacterById(ownerCharId);
        if (owner == null) {
            entry.following = false;
            return;
        }

        // Map change — teleport instantly, reset all motion state
        if (bot.getMapId() != owner.getMapId()) {
            bot.changeMap(owner.getMap(), owner.getPosition());
            entry.inAir     = false;
            entry.climbing  = false;
            entry.velY      = 0f;
            entry.wasMovingX = false;
            return;
        }

        // Rebuild foothold index on map change
        if (entry.lastMapId != bot.getMapId()) {
            entry.fhIndex  = buildFhIndex(bot.getMap());
            entry.lastMapId = bot.getMapId();
        }

        Point botPos   = bot.getPosition();
        Point ownerPos = owner.getPosition();

        if (entry.climbing) {
            tickClimbing(entry, bot, botPos, ownerPos);
        } else if (entry.inAir) {
            int stepX = calcStepX(entry, botPos.x, ownerPos.x);
            tickAirborne(entry, bot, botPos, botPos.x + stepX, ownerPos.x - botPos.x);
        } else {
            tickGrounded(entry, bot, botPos, ownerPos);
        }
    }

    // -------------------------------------------------------------------------
    // Rope climbing
    // -------------------------------------------------------------------------

    private void tickClimbing(BotEntry entry, Character bot, Point botPos, Point ownerPos) {
        int dy = ownerPos.y - botPos.y;

        // Done: reached rope top or close enough vertically
        if (botPos.y <= entry.climbTopY || dy > -cfg.JUMP_Y_THRESH) {
            entry.climbing = false;
            entry.inAir    = true;
            entry.velY     = 0f;
            return;
        }

        int newY = botPos.y - cfg.CLIMB_SPEED;
        bot.setPosition(new Point(entry.climbX, newY));
        bot.setStance(16);
        broadcastMovement(bot, 0, -cfg.CLIMB_VEL);
    }

    // -------------------------------------------------------------------------
    // Airborne physics
    // -------------------------------------------------------------------------

    private void tickAirborne(BotEntry entry, Character bot, Point botPos, int newX, int dx) {
        // Mid-air rope catch — grab any rope we're passing through
        for (Rope rope : bot.getMap().getRopes()) {
            if (Math.abs(rope.x() - botPos.x) <= cfg.ROPE_GRAB_X
                    && botPos.y >= rope.topY() && botPos.y <= rope.bottomY()) {
                entry.climbing  = true;
                entry.inAir     = false;
                entry.velY      = 0f;
                entry.climbX    = rope.x();
                entry.climbTopY = rope.topY();
                bot.setPosition(new Point(rope.x(), botPos.y));
                bot.setStance(16);
                broadcastMovement(bot, 0, -cfg.CLIMB_VEL);
                return;
            }
        }

        entry.velY = Math.min(entry.velY + cfg.GRAVITY, cfg.MAX_FALL);
        int newY   = botPos.y + (int) entry.velY;

        // Landing check — search strictly below current Y to avoid immediately re-landing
        // on the foothold we just left (botPos.y + 1, not botPos.y - 1)
        if (entry.velY > 0) {
            Point floorPt = bot.getMap().getPointBelow(new Point(newX, botPos.y + 1));
            if (floorPt != null && floorPt.y <= newY) {
                entry.inAir      = false;
                entry.velY       = 0f;
                entry.jumpCooldown = 0;
                bot.setPosition(new Point(newX, floorPt.y));
                bot.setStance(5);
                broadcastMovement(bot, 0, 0);
                return;
            }
        }

        bot.setPosition(new Point(newX, newY));
        bot.setStance(dx >= 0 ? 6 : 7);
        int velXBcast = dx >= 0 ? cfg.WALK_VEL : -cfg.WALK_VEL;
        int velYBcast = (int) (entry.velY * (1000f / cfg.TICK_MS));
        broadcastMovement(bot, velXBcast, velYBcast);
    }

    // -------------------------------------------------------------------------
    // Ground movement with foothold awareness
    // -------------------------------------------------------------------------

    private void tickGrounded(BotEntry entry, Character bot, Point botPos, Point ownerPos) {
        int dx = ownerPos.x - botPos.x;
        int dy = ownerPos.y - botPos.y; // negative = owner is higher on screen

        Foothold currentFh = bot.getMap().getFootholds()
                .findBelow(new Point(botPos.x, botPos.y - 1));
        if (currentFh == null) {
            entry.inAir = true;
            entry.velY  = 0f;
            return;
        }

        if (entry.jumpCooldown > 0) entry.jumpCooldown--;

        // Rope check — prefer rope when target is meaningfully above (2× jump threshold)
        if (dy < -cfg.JUMP_Y_THRESH * 2) {
            Rope rope = findNearbyRope(bot, botPos, ownerPos.y);
            if (rope != null) {
                int rdx = rope.x() - botPos.x;
                if (Math.abs(rdx) < cfg.ROPE_GRAB_X) {
                    // At rope base — start climbing
                    entry.climbing  = true;
                    entry.climbX    = rope.x();
                    entry.climbTopY = rope.topY();
                    bot.setPosition(new Point(rope.x(), botPos.y));
                    bot.setStance(16);
                    broadcastMovement(bot, 0, -cfg.CLIMB_VEL);
                    return;
                }
                // Walk toward rope X — only commit if the path is clear
                int stepToRope = Math.min(Math.abs(rdx), cfg.STEP) * (rdx >= 0 ? 1 : -1);
                int newX = botPos.x + stepToRope;
                Point snapped = bot.getMap().getPointBelow(new Point(newX, botPos.y - 1));
                if (snapped != null && snapped.y <= botPos.y + cfg.MAX_SNAP_DROP) {
                    bot.setPosition(new Point(newX, snapped.y));
                    bot.setStance(rdx >= 0 ? 2 : 3);
                    broadcastMovement(bot, rdx >= 0 ? cfg.WALK_VEL : -cfg.WALK_VEL, 0);
                    return;
                }
                // Path to rope blocked — fall through to jump logic
            }
        }

        // Proactive jump — only when path ahead is actually blocked (not a walkable slope)
        if (dy < -cfg.JUMP_Y_THRESH && entry.jumpCooldown == 0) {
            int stepX = calcStepX(entry, botPos.x, ownerPos.x);
            if (stepX != 0 && !isPathWalkable(bot, botPos, stepX)) {
                entry.jumpCooldown = cfg.JUMP_COOLDOWN;
                initiateJump(entry, bot, dx);
                return;
            }
        }

        // Close enough — stand still (STOP_DIST < FOLLOW_DIST gives a dead zone)
        if (Math.abs(dx) < cfg.STOP_DIST && Math.abs(dy) < cfg.STOP_DIST) {
            entry.wasMovingX = false;
            bot.setStance(5);
            broadcastMovement(bot, 0, 0);
            return;
        }

        int stepX = calcStepX(entry, botPos.x, ownerPos.x);
        int newX  = botPos.x + stepX;

        // Foothold edge detection
        boolean towardRightEdge = stepX > 0 && newX >= currentFh.getX2();
        boolean towardLeftEdge  = stepX < 0 && newX <= currentFh.getX1();

        if (towardRightEdge || towardLeftEdge) {
            int neighborId  = towardRightEdge ? currentFh.getNext() : currentFh.getPrev();
            Foothold neighbor = entry.fhIndex.get(neighborId);

            if (neighbor != null && !neighbor.isWall()) {
                int yDiff = neighbor.getY1() - currentFh.getY1();
                if (yDiff < -cfg.JUMP_Y_THRESH && entry.jumpCooldown == 0) {
                    initiateJump(entry, bot, dx);
                    return;
                }
                // Same level or gradual slope — walk naturally
            } else if (dy < -cfg.JUMP_Y_THRESH && entry.jumpCooldown == 0) {
                initiateJump(entry, bot, dx);
                return;
            }
        }

        // Normal ground walk — snap Y to terrain at newX
        Point snapped = bot.getMap().getPointBelow(new Point(newX, botPos.y - 1));
        if (snapped == null) {
            entry.inAir = true;
            entry.velY  = 0f;
            return;
        }
        if (snapped.y > botPos.y + cfg.MAX_SNAP_DROP) {
            // Walked off ledge — fall naturally instead of snapping
            entry.inAir = true;
            entry.velY  = 0f;
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
        Point next = bot.getMap().getPointBelow(new Point(botPos.x + stepX, botPos.y - 1));
        if (next == null) return false;
        int dy = next.y - botPos.y;
        // Walkable if we're going slightly downhill (< MAX_SNAP_DROP) or uphill (< MAX_SLOPE_UP)
        return dy <= cfg.MAX_SNAP_DROP && dy >= -cfg.MAX_SLOPE_UP;
    }

    private void initiateJump(BotEntry entry, Character bot, int dx) {
        entry.velY  = -cfg.JUMP_FORCE;
        entry.inAir = true;
        bot.setStance(dx >= 0 ? 6 : 7);
        int jumpVelY = -(int) (cfg.JUMP_FORCE * (1000f / cfg.TICK_MS));
        broadcastMovement(bot, dx >= 0 ? cfg.WALK_VEL : -cfg.WALK_VEL, jumpVelY);
    }

    private Rope findNearbyRope(Character bot, Point botPos, int targetY) {
        Rope best    = null;
        int bestDist = cfg.ROPE_SEEK_X + 1;
        for (Rope r : bot.getMap().getRopes()) {
            int dist = Math.abs(r.x() - botPos.x);
            if (dist < bestDist && r.topY() <= targetY && r.bottomY() >= botPos.y - 40) {
                bestDist = dist;
                best     = r;
            }
        }
        return best;
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

    private static Map<Integer, Foothold> buildFhIndex(MapleMap map) {
        Map<Integer, Foothold> index = new HashMap<>();
        for (Foothold fh : map.getFootholds().getAllFootholds()) {
            index.put(fh.getId(), fh);
        }
        return index;
    }
}
