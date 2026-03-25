package server.bots;

import client.BotClient;
import client.Character;
import client.Skill;
import client.SkillFactory;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.keybind.KeyBinding;
import constants.inventory.ItemConstants;
import io.netty.buffer.Unpooled;
import net.packet.ByteBufInPacket;
import net.packet.InPacket;
import net.packet.Packet;
import net.server.Server;
import net.server.channel.handlers.AbstractDealDamageHandler;
import net.server.channel.handlers.CloseRangeDamageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ItemInformationProvider;
import server.StatEffect;
import server.TimerManager;
import server.life.Monster;
import server.maps.MapItem;
import server.maps.MapleMap;
import tools.PacketCreator;

import java.awt.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BotManager {
    // TODO: list from most important to least important
    // TODO: Option to respec bot ap/sp
    // TODO: Make bot auto scan/autoequip the "best" equipment they have + can equip in their inventory
    // TODO: Option to ask bot to drop their entire equip inventory/only scrolls/only potions/specific item/ etc, some useful options, remember to skip untradable/quest items
    // TODO: some kind of help command/question on available interactions available (can ask to drop, respec, change build, check stats, etc)
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
        public float KNOCKBACK_RISE = 18f; // upward velocity applied on mob knockback (~1/3 jump)

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
        public int   DEAD_STANCE   = 0;    // stance for tombstone (death) state
        public int   STAND_STANCE  = 5;    // default standing stance
        public int   PRONE_STANCE  = 10;   // stance before down-jump: confirmed state=10 = down-held/crouch on ground
        public int   LEDGE_SEEK_X  = 150; // px; if foothold edge is within this radius, walk off it instead of down-jumping

        // Stuck recovery
        public int   STUCK_CHECK_INTERVAL = 30;  // ticks between stuck-position checks
        public int   STUCK_CHASE_TICKS    = 60;  // ticks of raw-chase mode after stuck detected
        public int   STUCK_MIN_MOVE       = 20;  // px; moved less than this in N ticks = stuck
        public int   STUCK_WALKBACK_LIMIT = 200; // px; max backward travel allowed during raw-chase

        // Waypoint (1-hop pathfinding to a rope outside normal detection range)
        public int   WAYPOINT_SEEK_X  = 1000;  // expanded rope search radius when setting a waypoint
        public int   WAYPOINT_TIMEOUT = 80;    // ticks before an unreached waypoint expires (~8s)
        public int   WAYPOINT_MIN_DY  = 400;   // px; min height above bot before considering a waypoint rope

        // Grind mode
        public int   ATTACK_RANGE_X     = 80;   // px; horizontal attack reach
        public int   ATTACK_RANGE_Y     = 50;    // px; vertical reach upward (tight — same level to slightly above)
        public int   ATTACK_DOWN_MAX    = 20;    // px; max downward tolerance (no attacking far below)
        public int   ATTACK_JUMP_Y      = 130;   // px; jump toward target if it is this far above and in X range
        public int   ATTACK_COOLDOWN    = 10;    // ticks between attacks (~800ms at 100ms/tick)
        public int   GRIND_SEEK_RANGE   = 800;   // px; monster search radius
        public int   AOE_MOB_THRESHOLD  = 2;     // nearby mobs needed to prefer AoE skill over single-target
        // TODO: Aoe range/area/hitbox for attack and skill should be read from actual game data
        public long  AOE_RANGE_SQ       = 90000L; // px² AoE sweep radius (~300px)

        // Mob damage taken
        public int   MOB_TOUCH_HALF_W = 40;    // px; approximate half-width of mob bounding box
        public int   MOB_TOUCH_HALF_H = 30;    // px; approximate half-height of mob bounding box
        public int   MOB_HIT_COOLDOWN = 15;    // ticks between mob hits (~1.5s)
        public long  BOT_DEAD_MS      = 10_000L; // ms bot stays dead before respawning

        // Passive loot
        public int   LOOT_RADIUS      = 100;   // px; pickup items within this box radius
        public int   INV_FULL_WARN_CD = 100;   // ticks between "inventory full" complaints

        // Potion management
        public int   POT_LOW_WARN     = 100;   // warn on grind start below this count
        public int   POT_STOP         = 10;    // stop grinding below this HP pot count
        public int   POT_CHECK_TICKS  = 20;    // ticks between potion recount/rebind
        public float AUTOPOT_HP_THRESH = 0.7f; // use HP pot when HP falls below this ratio
        public float AUTOPOT_MP_THRESH = 0.5f; // use MP pot when MP falls below this ratio
    }

    /** Singleton config — replace with `cfg = new Config()` after hotswapping to reset. */
    public static Config cfg = new Config();

    public static BotManager getInstance() { return instance; }

    // ownerCharId → list of owned bot entries (1:N)
    private final Map<Integer, List<BotEntry>> bots = new ConcurrentHashMap<>();

    private static final Pattern DISMISS_PATTERN = Pattern.compile(
            "\\b(dismiss|disown|release)\\s+(\\S+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RECRUIT_PATTERN = Pattern.compile(
            "\\b(recruit|adopt|hire|claim)\\s+(\\S+)\\b", Pattern.CASE_INSENSITIVE);
    // "give Jason Bob" or "transfer Jason to Bob"
    private static final Pattern GIVE_PATTERN = Pattern.compile(
            "\\b(give|transfer)\\s+(\\S+)(?:\\s+to)?\\s+(\\S+)\\b", Pattern.CASE_INSENSITIVE);

    private static final List<String> DEATH_REPLIES = List.of(
            "oops im dead", "gg", "rip me", "oww", "i died lol",
            "welp", "ouchh", "nooo", "ok i died", "i'll be right back");

    static String randomReply(List<String> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void registerBot(int ownerCharId, Character owner, Character bot) {
        List<BotEntry> entries = bots.computeIfAbsent(ownerCharId, k -> new CopyOnWriteArrayList<>());
        // Replace if same bot character is already registered (e.g. relog)
        entries.removeIf(e -> {
            if (e.bot.getId() == bot.getId()) { e.task.cancel(false); return true; }
            return false;
        });
        int botCharId = bot.getId();
        ScheduledFuture<?> task = TimerManager.getInstance().register(
                () -> tick(ownerCharId, botCharId), cfg.TICK_MS);
        BotEntry entry = new BotEntry(bot, owner, task);
        entries.add(entry);
        TimerManager.getInstance().schedule(() -> BotChatManager.checkBotStatus(entry, bot), 2000);
    }

    public void removeBot(int ownerCharId) {
        List<BotEntry> entries = bots.remove(ownerCharId);
        if (entries != null) entries.forEach(e -> e.task.cancel(false));
    }

    /** Cancel and remove a bot by the bot character's own ID (used during shutdown/disconnect). */
    public void removeBotByCharId(int botCharId) {
        for (List<BotEntry> entries : bots.values()) {
            entries.removeIf(e -> {
                if (e.bot.getId() == botCharId) { e.task.cancel(false); return true; }
                return false;
            });
        }
    }

    /** Disown a bot by name — cancels its AI tick and leaves it idle in the map. */
    public boolean dismissBot(int ownerCharId, String botName) {
        List<BotEntry> entries = bots.get(ownerCharId);
        if (entries == null) return false;
        BotEntry found = null;
        for (BotEntry e : entries) {
            if (e.bot.getName().equalsIgnoreCase(botName)) { found = e; break; }
        }
        if (found == null) return false;
        BotEntry entry = found;
        entries.remove(entry);
        entry.task.cancel(false);
        entry.following = false;
        entry.grinding  = false;
        TimerManager.getInstance().schedule(() ->
                botSay(entry.bot, randomReply(List.of("ok", "sure", "alright", "gotcha"))), 500);
        return true;
    }

    /** Recruit an ownerless bot by name into the owner's group. */
    public boolean recruitBot(int ownerCharId, Character owner, String botName) {
        Character bot = findOwnerlessBot(botName, owner.getWorld());
        if (bot == null) return false;
        registerBot(ownerCharId, owner, bot);
        return true;
    }

    /** Transfer a bot from this owner to another player in the same map. Returns an error string on failure, null on success. */
    public String giveBot(int ownerCharId, Character owner, String botName, String targetName) {
        List<BotEntry> entries = bots.get(ownerCharId);
        if (entries == null) return "You have no bots.";
        BotEntry found = null;
        for (BotEntry e : entries) {
            if (e.bot.getName().equalsIgnoreCase(botName)) { found = e; break; }
        }
        if (found == null) return "No bot named '" + botName + "' in your group.";

        // Find target player in the same map
        Character target = owner.getMap().getCharacterByName(targetName);
        if (target == null) return "Player '" + targetName + "' not found in this map.";
        if (target.getId() == ownerCharId) return "That's you.";

        // Disown from current owner
        Character bot = found.bot;
        entries.remove(found);
        found.task.cancel(false);
        found.following = false;
        found.grinding  = false;

        // Register under new owner
        registerBot(target.getId(), target, bot);
        TimerManager.getInstance().schedule(() ->
                botSay(bot, randomReply(List.of("ok!", "sure!", "hey " + target.getName() + "!", "hi " + target.getName() + "!"))), 800);
        return null;
    }

    /** Finds a bot-client character with the given name that is not currently owned by anyone. */
    private Character findOwnerlessBot(String name, int world) {
        for (var ch : Server.getInstance().getWorld(world).getChannels()) {
            Character c = ch.getPlayerStorage().getCharacterByName(name);
            if (c == null || !(c.getClient() instanceof BotClient)) continue;
            // Check not already owned
            boolean owned = false;
            outer:
            for (List<BotEntry> entries : bots.values()) {
                for (BotEntry e : entries) {
                    if (e.bot.getId() == c.getId()) { owned = true; break outer; }
                }
            }
            if (!owned) return c;
        }
        return null;
    }

    public Character getBot(int ownerCharId) {
        List<BotEntry> entries = bots.get(ownerCharId);
        return (entries != null && !entries.isEmpty()) ? entries.get(0).bot : null;
    }

    public void handleChat(Character owner, String message) {
        // Recruit must work even when owner has no bots yet
        Matcher rm = RECRUIT_PATTERN.matcher(message);
        if (rm.find()) {
            String name = rm.group(2);
            if (recruitBot(owner.getId(), owner, name)) {
                owner.yellowMessage("Bot '" + name + "' recruited!");
            } else {
                owner.yellowMessage("No ownerless bot named '" + name + "' found.");
            }
            return;
        }

        Matcher gm = GIVE_PATTERN.matcher(message);
        if (gm.find()) {
            String err = giveBot(owner.getId(), owner, gm.group(2), gm.group(3));
            if (err != null) owner.yellowMessage(err);
            else owner.yellowMessage("Bot '" + gm.group(2) + "' transferred to " + gm.group(3) + ".");
            return;
        }

        List<BotEntry> entries = bots.get(owner.getId());
        if (entries == null || entries.isEmpty()) return;

        // Dismiss: disown bot, leaves it idle in map
        Matcher dm = DISMISS_PATTERN.matcher(message);
        if (dm.find()) {
            String name = dm.group(2);
            if (dismissBot(owner.getId(), name)) {
                owner.yellowMessage("Bot '" + name + "' disowned — now idle.");
            } else {
                owner.yellowMessage("No bot named '" + name + "' in your group.");
            }
            return;
        }

        // Name-prefix routing: "Jason pots?" → only Jason responds
        String lowerMsg = message.toLowerCase();
        for (BotEntry entry : entries) {
            String name = entry.bot.getName().toLowerCase();
            if (lowerMsg.startsWith(name) && lowerMsg.length() > name.length()) {
                char next = lowerMsg.charAt(name.length());
                if (next == ' ' || next == ',' || next == '!' || next == '?') {
                    String rest = message.substring(name.length()).replaceFirst("^[,!?\\s]+", "").trim();
                    if (!rest.isEmpty()) {
                        BotChatManager.handleChat(entry, rest);
                        return;
                    }
                }
            }
        }

        // No name prefix — broadcast to all bots
        for (BotEntry entry : entries) {
            BotChatManager.handleChat(entry, message);
        }
    }

    // -------------------------------------------------------------------------
    // Main tick
    // -------------------------------------------------------------------------

    private void tick(int ownerCharId, int botCharId) {
        List<BotEntry> entries = bots.get(ownerCharId);
        if (entries == null) return;
        BotEntry entry = null;
        for (BotEntry e : entries) {
            if (e.bot.getId() == botCharId) { entry = e; break; }
        }
        if (entry == null) return;
        if (entry.skipTicks > 0) { entry.skipTicks--; return; }
        Character bot = entry.bot;

        Character owner = entry.owner;
        if (owner == null || owner.getId() != ownerCharId || !owner.isLoggedinWorld()) {
            owner = Server.getInstance()
                    .getWorld(bot.getWorld())
                    .getPlayerStorage()
                    .getCharacterById(ownerCharId);
            entry.owner = owner;
        }
        if (owner == null) {
            entry.following = false;
            return;
        }

        // Dead state: skip AI until respawn timer expires.
        // Also catch stale hp=0 (e.g. deadUntil was lost on save/reconnect) — re-enter dead state.
        if (entry.deadUntil == 0 && bot.getHp() <= 0) {
            bot.setStance(cfg.DEAD_STANCE);
            broadcastMovement(bot, 0, 0);
            entry.deadUntil = System.currentTimeMillis() + cfg.BOT_DEAD_MS;
            BotMovementManager.resetEntryState(entry);
        }
        if (entry.deadUntil > 0) {
            if (System.currentTimeMillis() >= entry.deadUntil) {
                respawnBot(entry, bot, owner);
            }
            return;
        }

        Point botPos    = bot.getPosition();
        Point targetPos = owner.getPosition();

        // These run in all modes (idle, follow, grind)
        tickMobDamage(entry, bot);
        tickPassiveLoot(entry, bot);
        tickPotionCheck(entry, bot);
        BotBuildManager.checkLevelUp(entry, bot);
        BotChatManager.tickAfkCheck(entry, owner);
        rebuildSkillCacheIfNeeded(entry, bot);
        tickBuffs(entry, bot);

        if (!entry.following && !entry.grinding) {
            if (entry.inAir) {
                BotMovementManager.tickAirborne(entry, targetPos);
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
                bot.setStance(cfg.STAND_STANCE); // ensure spawn packet shows stand stance, not walk
                bot.changeMap(owner.getMap(), spawn);
                BotMovementManager.resetEntryState(entry);
                return;
            }
        }
        // Teleport if hopelessly far — applies to both follow and grind (catches falling off map)
        if (Math.abs(botPos.x - targetPos.x) + Math.abs(botPos.y - targetPos.y) > cfg.TELEPORT_DIST) {
            Point spawn = new Point(targetPos.x, targetPos.y - 10);
            bot.setPosition(spawn);
            BotMovementManager.resetEntryState(entry);
            broadcastMovement(bot, 0, 0);
            return;
        }

        // Rebuild foothold index on map change
        if (entry.lastMapId != bot.getMapId()) {
            entry.fhIndex  = BotMovementManager.buildFhIndex(bot.getMap());
            entry.lastMapId = bot.getMapId();
        }

        // Grind mode: navigate toward nearest monster, attack when in range
        if (entry.grinding) {
            // Stick to current target while it's alive and in range; only re-pick when needed
            double seekRangeSq = (double) cfg.GRIND_SEEK_RANGE * cfg.GRIND_SEEK_RANGE;
            Monster target = entry.grindTarget;
            if (target == null || !target.isAlive()
                    || target.getPosition().distanceSq(botPos) > seekRangeSq) {
                target = findGrindTarget(bot);
            }
            if (target == null) {
                if (entry.inAir) BotMovementManager.tickAirborne(entry, targetPos);
                else { bot.setStance(5); broadcastMovement(bot, 0, 0); }
                return;
            }
            entry.grindTarget = target;
            Point tp = target.getPosition();
            int dx = Math.abs(tp.x - botPos.x);
            int dy = botPos.y - tp.y; // positive = target is higher on screen (above bot)

            if (!entry.climbing) {
                boolean inHRange = dx <= cfg.ATTACK_RANGE_X;
                boolean inVRange = dy >= -cfg.ATTACK_DOWN_MAX && dy <= cfg.ATTACK_RANGE_Y;
                boolean jumpable = dy > cfg.ATTACK_RANGE_Y && dy <= cfg.ATTACK_JUMP_Y;

                if (inHRange && inVRange) {
                    // In range — attack if grounded, or during ascent of a jump
                    if (!entry.inAir || entry.velY < 0) {
                        attackMonster(entry, bot, target);
                        if (!entry.inAir) return;
                        // airborne: fall through so tickAirborne still runs this tick
                    }
                } else if (!entry.inAir && inHRange && jumpable && entry.jumpCooldown == 0) {
                    // Target is above but within jump height — jump toward it
                    BotMovementManager.initiateJump(entry, bot, tp.x - botPos.x);
                    return;
                }
            }
            targetPos = tp;
        }

        // Shift target by bot's personal offset so multiple bots spread out (follow + grind)
        if (entry.following || entry.grinding) {
            targetPos = new Point(targetPos.x + entry.followOffsetX, targetPos.y);
        }

        if (entry.climbing) {
            BotMovementManager.tickClimbing(entry, targetPos);
        } else if (entry.inAir) {
            BotMovementManager.tickAirborne(entry, targetPos);
        } else {
            BotMovementManager.tickGrounded(entry, targetPos);
        }
    }

    // -------------------------------------------------------------------------
    // Grind mode helpers
    // -------------------------------------------------------------------------

    /** Returns a random monster from the nearest 3 within seek range, so multiple bots spread across different targets. */
    private Monster findGrindTarget(Character bot) {
        Point botPos = bot.getPosition();
        double rangeSq = (double) cfg.GRIND_SEEK_RANGE * cfg.GRIND_SEEK_RANGE;
        List<Monster> candidates = new ArrayList<>();
        for (Monster m : bot.getMap().getAllMonsters()) {
            if (m.isAlive() && m.getPosition().distanceSq(botPos) <= rangeSq) candidates.add(m);
        }
        if (candidates.isEmpty()) return null;
        candidates.sort((a, b) -> Double.compare(a.getPosition().distanceSq(botPos), b.getPosition().distanceSq(botPos)));
        return candidates.get(ThreadLocalRandom.current().nextInt(Math.min(3, candidates.size())));
    }

    private void attackMonster(BotEntry entry, Character bot, Monster target) {
        if (entry.attackCooldown > 0) {
            entry.attackCooldown--;
            return;
        }

        int watk   = bot.getTotalWatk();
        int maxDmg = Math.max(1, bot.calculateMaxBaseDamage(watk));
        int minDmg = Math.max(1, bot.calculateMinBaseDamage(watk));

        // --- skill selection ---
        // TODO: have a dynamic sized list of available skills in case of cooldown heavy class with many skills
        int chosenSkill = 0;
        int chosenLevel = 0;
        int numDmg      = 1;
        List<Monster> aoeTargets = null;

        // Try AoE skill first if enough nearby monsters
        if (entry.aoeSkillId != 0 && !bot.skillIsCooling(entry.aoeSkillId)) {
            List<Monster> nearby = new ArrayList<>();
            for (Monster m : bot.getMap().getAllMonsters()) {
                if (m.isAlive() && distSq(m.getPosition(), target.getPosition()) <= cfg.AOE_RANGE_SQ) {
                    nearby.add(m);
                    if (nearby.size() >= entry.aoeSkillMobs) break;
                }
            }
            if (nearby.size() >= cfg.AOE_MOB_THRESHOLD) {
                chosenSkill = entry.aoeSkillId;
                chosenLevel = bot.getSkillLevel(chosenSkill);
                numDmg      = Math.max(1, SkillFactory.getSkill(chosenSkill).getEffect(chosenLevel).getAttackCount());
                aoeTargets  = nearby;
            }
        }
        // Fall back to single-target attack skill
        if (chosenSkill == 0 && entry.attackSkillId != 0 && !bot.skillIsCooling(entry.attackSkillId)) {
            chosenSkill = entry.attackSkillId;
            chosenLevel = bot.getSkillLevel(chosenSkill);
            numDmg      = Math.max(1, SkillFactory.getSkill(chosenSkill).getEffect(chosenLevel).getAttackCount());
        }
        // chosenSkill == 0 → basic attack (skill=0, numDmg=1)

        // --- build attack ---
        int numAttacked = aoeTargets != null ? aoeTargets.size() : 1;
        AbstractDealDamageHandler.AttackInfo attack = new AbstractDealDamageHandler.AttackInfo();
        attack.skill                = chosenSkill;
        attack.skilllevel           = chosenLevel;
        attack.numDamage            = numDmg;
        attack.numAttacked          = numAttacked;
        attack.numAttackedAndDamage = (numAttacked << 4) | numDmg;
        attack.speed                = 4;
        attack.stance    = bot.getPosition().x > target.getPosition().x ? -128 : 0;
        attack.direction = bot.getPosition().x > target.getPosition().x ? 17 : 6;
        attack.targets   = new HashMap<>();

        if (aoeTargets != null) {
            for (Monster m : aoeTargets) {
                attack.targets.put(m.getObjectId(), makeTarget(numDmg, minDmg, maxDmg));
            }
            // Actual count may differ if primary target wasn't in the nearby list
            if (!attack.targets.containsKey(target.getObjectId())) {
                attack.targets.put(target.getObjectId(), makeTarget(numDmg, minDmg, maxDmg));
            }
            attack.numAttacked          = attack.targets.size();
            attack.numAttackedAndDamage = (attack.numAttacked << 4) | numDmg;
        } else {
            attack.targets.put(target.getObjectId(), makeTarget(numDmg, minDmg, maxDmg));
        }

        CloseRangeDamageHandler.applyCloseRangeEffects(attack, bot, bot.getClient());
        entry.attackCooldown = cfg.ATTACK_COOLDOWN;
    }

    private AbstractDealDamageHandler.AttackTarget makeTarget(int hits, int minDmg, int maxDmg) {
        List<Integer> lines = new ArrayList<>(hits);
        for (int i = 0; i < hits; i++) {
            lines.add(minDmg < maxDmg
                    ? ThreadLocalRandom.current().nextInt(minDmg, maxDmg + 1)
                    : maxDmg);
        }
        return new AbstractDealDamageHandler.AttackTarget((short) 305, lines);
    }

    static long distSq(Point a, Point b) {
        long dx = a.x - b.x, dy = a.y - b.y;
        return dx * dx + dy * dy;
    }

    // -------------------------------------------------------------------------
    // Damage taken
    // -------------------------------------------------------------------------

    /** Check every alive monster on the map; if bot is inside its bounding box, apply a hit. */
    private void tickMobDamage(BotEntry entry, Character bot) {
        if (entry.mobHitCooldown > 0) {
            entry.mobHitCooldown--;
            return;
        }
        if (bot.getHp() <= 0) return;

        Point botPos = bot.getPosition();
        for (Monster mob : bot.getMap().getAllMonsters()) {
            if (!mob.isAlive()) continue;
            Point mp = mob.getPosition();
            if (Math.abs(botPos.x - mp.x) <= cfg.MOB_TOUCH_HALF_W
                    && Math.abs(botPos.y - mp.y) <= cfg.MOB_TOUCH_HALF_H) {
                applyMobHit(entry, bot, mob);
                return;
            }
        }
    }

    /**
     * Apply one physical hit from {@code mob} to the bot.
     * Damage = PADamage ± 15% (v83 mob attack approximation; no server-side WDEF available).
     */
    void applyMobHit(BotEntry entry, Character bot, Monster mob) {
        int pa  = mob.getPADamage();
        int max = Math.max(1, pa * 115 / 100);
        int min = Math.max(1, pa *  85 / 100);
        int dmg = min < max ? ThreadLocalRandom.current().nextInt(min, max + 1) : max;

        bot.addMPHP(-dmg, 0);

        // direction: 0 = hit from left (knocked right), 1 = hit from right (knocked left)
        int dir = mob.getPosition().x < bot.getPosition().x ? 0 : 1;
        bot.getMap().broadcastMessage(bot,
                PacketCreator.damagePlayer(-1, mob.getId(), bot.getId(), dmg, 0,
                        dir, false, 0, false, 0, 0, 0), false);

        // Knock bot back — enter airborne with slight upward arc so physics land it correctly.
        // Never snap to ground: let tickAirborne handle landing to avoid falling through platforms.
        Point bp  = bot.getPosition();
        int   kbX = bp.x + (dir == 0 ? 30 : -30);
        bot.setPosition(new Point(kbX, bp.y));
        BotMovementManager.resetEntryState(entry); // inAir=true, velY=0
        entry.velY    = -cfg.KNOCKBACK_RISE;        // slight upward kick
        entry.airVelX = (dir == 0 ? 1 : -1) * cfg.STEP; // carry horizontal momentum
        int velXBcast = entry.airVelX * (1000 / cfg.TICK_MS);
        int velYBcast = (int) (-entry.velY * (1000f / cfg.TICK_MS));
        broadcastMovement(bot, velXBcast, velYBcast);

        entry.mobHitCooldown = cfg.MOB_HIT_COOLDOWN;

        if (bot.getHp() <= 0) {
            bot.setStance(cfg.DEAD_STANCE);
            broadcastMovement(bot, 0, 0);
            botSay(bot, randomReply(DEATH_REPLIES));
            entry.deadUntil = System.currentTimeMillis() + cfg.BOT_DEAD_MS;
            BotMovementManager.resetEntryState(entry);
        }
    }

    void reloginBot(int charId, int ownerCharId, int world, int channel) {
        Character owner = Server.getInstance()
                .getWorld(world)
                .getPlayerStorage()
                .getCharacterById(ownerCharId);
        if (owner == null) return; // owner logged off — skip

        try {
            BotClient botClient = new BotClient(world, channel);
            Character botChar = Character.loadCharFromDB(charId, botClient, true);
            botClient.setPlayer(botChar);
            botClient.setAccID(botChar.getAccountID());

            MapleMap map = owner.getMap();
            Point pos = map.getPointBelow(new Point(owner.getPosition().x, owner.getPosition().y - 1));
            if (pos == null) pos = owner.getPosition();

            botChar.setMapId(map.getId());
            botChar.newClient(botClient);
            botChar.recalcLocalStats();
            botChar.setPosition(pos);

            var channelServer = Server.getInstance().getChannel(world, channel);
            channelServer.addPlayer(botChar);
            channelServer.getWorldServer().addPlayer(botChar);
            botChar.setEnteredChannelWorld();
            map.addPlayer(botChar);
            botChar.broadcastStance();

            registerBot(ownerCharId, owner, botChar);
            TimerManager.getInstance().schedule(() -> botSay(botChar, "back!!"), 1000);
        } catch (SQLException e) {
            log.warn("reloginBot: failed to reload charId={}", charId, e);
        }
    }

    private void respawnBot(BotEntry entry, Character bot, Character owner) {
        entry.deadUntil = 0;
        bot.updateHp(bot.getMaxHp());

        if (bot.getMapId() != owner.getMapId()) {
            bot.forceChangeMap(owner.getMap(), owner.getMap().findClosestPortal(owner.getPosition()));
        }
        Point ownerPos = owner.getPosition();
        Point spawnPos = bot.getMap().getPointBelow(new Point(ownerPos.x, ownerPos.y - 1));
        bot.setPosition(spawnPos != null ? spawnPos : ownerPos);
        BotMovementManager.resetEntryState(entry);
        bot.setStance(cfg.STAND_STANCE); // clears tombstone for nearby clients
        broadcastMovement(bot, 0, 0);
        botSay(bot, "back!");
    }

    // -------------------------------------------------------------------------
    // Potion management
    // -------------------------------------------------------------------------

    /**
     * Counts HP and MP potions in the bot's USE inventory.
     * Items restoring both (elixirs) count toward both totals.
     * @return int[2]: [hpCount, mpCount]
     */
    int[] countPotions(Character bot) {
        int hp = 0, mp = 0;
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        for (Item item : bot.getInventory(InventoryType.USE).list()) {
            StatEffect eff;
            try { eff = ii.getItemEffect(item.getItemId()); } catch (Exception e) { continue; }
            if (eff == null) continue;
            int qty = item.getQuantity();
            if (eff.getHp() > 0 || eff.getHpRate() > 0) hp += qty;
            if (eff.getMp() > 0 || eff.getMpRate() > 0) mp += qty;
        }
        return new int[]{hp, mp};
    }

    /**
     * Binds the best HP/MP potions from inventory to autopot keymap slots 91/92.
     * Called on grind start and every POT_CHECK_TICKS to handle type depletion.
     */
    void setupAutopotForBot(Character bot) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        int hpItemId = -1, mpItemId = -1;
        int bestHp = 0, bestMp = 0;
        for (Item item : bot.getInventory(InventoryType.USE).list()) {
            if (item.getQuantity() <= 0) continue;
            StatEffect eff;
            try { eff = ii.getItemEffect(item.getItemId()); } catch (Exception e) { continue; }
            if (eff == null) continue;
            if (eff.getHp() > bestHp) { bestHp = eff.getHp(); hpItemId = item.getItemId(); }
            if (eff.getMp() > bestMp) { bestMp = eff.getMp(); mpItemId = item.getItemId(); }
        }
        if (hpItemId > 0) {
            bot.changeKeybinding(91, new KeyBinding(2, hpItemId));
            bot.setAutopotHpAlert(cfg.AUTOPOT_HP_THRESH);
        } else {
            bot.getKeymap().remove(91);
            bot.setAutopotHpAlert(0f);
        }
        if (mpItemId > 0) {
            bot.changeKeybinding(92, new KeyBinding(2, mpItemId));
            bot.setAutopotMpAlert(cfg.AUTOPOT_MP_THRESH);
        } else {
            bot.getKeymap().remove(92);
            bot.setAutopotMpAlert(0f);
        }
    }

    private static final List<String> GRIND_REPLIES = List.of(
            "ok", "on it", "lets get it", "farming time", "got it",
            "sure", "ok boss", "time to grind");

    /** Builds grind-start reply with low-potion warning when below POT_LOW_WARN. */
    String grindStartMessage(Character bot) {
        int[] pots = countPotions(bot);
        int hp = pots[0], mp = pots[1];
        String base = randomReply(GRIND_REPLIES);
        if (hp >= cfg.POT_LOW_WARN && mp >= cfg.POT_LOW_WARN) return base;
        StringBuilder msg = new StringBuilder(base).append(", but");
        if (hp < cfg.POT_LOW_WARN) msg.append(" only ").append(hp).append(" HP pots");
        if (hp < cfg.POT_LOW_WARN && mp < cfg.POT_LOW_WARN) msg.append(" and");
        if (mp < cfg.POT_LOW_WARN) msg.append(" only ").append(mp).append(" MP pots");
        return msg.append(" left").toString();
    }

    // -------------------------------------------------------------------------
    // Passive loot
    // -------------------------------------------------------------------------

    /** Picks up lootable drops within LOOT_RADIUS — runs every tick in all modes. */
    private void tickPassiveLoot(BotEntry entry, Character bot) {
        if (entry.invFullWarnCooldown > 0) entry.invFullWarnCooldown--;
        Point botPos = bot.getPosition();
        for (MapItem drop : bot.getMap().getDroppedItems()) {
            if (!drop.canBePickedBy(bot)) continue;
            if (System.currentTimeMillis() - drop.getDropTime() < 1000) continue; // wait 1s after spawn
            Point dp = drop.getPosition();
            if (Math.abs(dp.x - botPos.x) > cfg.LOOT_RADIUS
                    || Math.abs(dp.y - botPos.y) > cfg.LOOT_RADIUS) continue;
            if (drop.getMeso() <= 0 && drop.getItemId() > 0) {
                InventoryType type = ItemConstants.getInventoryType(drop.getItemId());
                Inventory inv = bot.getInventory(type);
                if (inv != null && inv.isFull()) {
                    if (entry.invFullWarnCooldown <= 0) {
                        botSay(bot, type.name().toLowerCase() + " inventory is full!");
                        entry.invFullWarnCooldown = cfg.INV_FULL_WARN_CD;
                    }
                    continue;
                }
            }
            bot.pickupItem(drop);
        }
    }

    // -------------------------------------------------------------------------
    // Potion check tick
    // -------------------------------------------------------------------------

    /** Periodically rebinds autopot and stops grinding when HP pots are critically low. */
    private void tickPotionCheck(BotEntry entry, Character bot) {
        if (entry.potCheckTimer > 0) { entry.potCheckTimer--; return; }
        entry.potCheckTimer = cfg.POT_CHECK_TICKS;

        setupAutopotForBot(bot);

        if (!entry.grinding) return;
        int[] pots = countPotions(bot);
        if (pots[0] < cfg.POT_STOP && bot.getHp() < bot.getMaxHp() * 0.5f) {
            entry.grinding = false;
            entry.following = true;
            botSay(bot, "out of HP pots!! walking to you");
        }
    }

    /**
     * Broadcasts a MOVE_PLAYER packet with real velocity values so the client
     * smoothly interpolates over TICK_MS ms — matching how real player packets work.
     *
     * AbsoluteLifeMovement layout (15 bytes total):
     *   numCmds(1) cmd(1) x(2) y(2) xv(2) yv(2) fh(2) stance(1) duration(2)
     */
    void broadcastMovement(Character bot, int velX, int velY) {
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

    // -------------------------------------------------------------------------
    // Skill cache
    // -------------------------------------------------------------------------

    private void rebuildSkillCacheIfNeeded(BotEntry entry, Character bot) {
        if (entry.cachedSkillJob == bot.getJob().getId()) return;
        entry.cachedSkillJob = bot.getJob().getId();

        entry.attackSkillId = 0;
        entry.aoeSkillId    = 0;
        entry.aoeSkillMobs  = 1;
        entry.buffSkillIds.clear();

        int bestAtkHits = 0;   // attackCount of best single-target skill
        int bestAoeScore = 0;  // mobCount * attackCount of best AoE skill

        for (Skill skill : bot.getSkills().keySet()) {
            int lvl = bot.getSkillLevel(skill);
            if (lvl <= 0) continue;

            StatEffect fx  = skill.getEffect(lvl);
            int atk  = fx.getAttackCount();
            int mobs = fx.getMobCount();
            int dur  = fx.getDuration();

            if (atk > 0) {
                if (mobs >= 2) {
                    int score = mobs * atk;
                    if (score > bestAoeScore) {
                        bestAoeScore     = score;
                        entry.aoeSkillId   = skill.getId();
                        entry.aoeSkillMobs = mobs;
                    }
                } else {
                    if (atk > bestAtkHits) {
                        bestAtkHits          = atk;
                        entry.attackSkillId  = skill.getId();
                    }
                }
            } else if (dur > 0) {
                // Timed buff with no attack component
                entry.buffSkillIds.add(skill.getId());
                entry.nextBuffAt.putIfAbsent(skill.getId(), 0L); // 0 = apply immediately
            }
            // passive (dur=0, atk=0) — skip
        }
    }

    // -------------------------------------------------------------------------
    // Auto-buff
    // -------------------------------------------------------------------------

    private void tickBuffs(BotEntry entry, Character bot) {
        if (!entry.following && !entry.grinding) return;   // idle — skip
        if (entry.buffSkillIds.isEmpty()) return;
        if (bot.getMap().getAllMonsters().stream().noneMatch(Monster::isAlive)) return;

        long now = System.currentTimeMillis();
        for (int skillId : entry.buffSkillIds) {
            if (now < entry.nextBuffAt.getOrDefault(skillId, 0L)) continue; // still active
            if (bot.skillIsCooling(skillId)) continue;

            Skill skill = SkillFactory.getSkill(skillId);
            int   lvl   = bot.getSkillLevel(skill);
            if (lvl <= 0) continue;

            StatEffect fx = skill.getEffect(lvl);
            fx.applyTo(bot, null);

            long dur = fx.getDuration();
            entry.nextBuffAt.put(skillId, now + (long)(dur * 0.9));

            if (fx.getCooldown() > 0) {
                bot.addCooldown(skillId, now, fx.getCooldown() * 1000L);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    void botSay(Character bot, String text) {
        bot.getMap().broadcastMessage(PacketCreator.getChatText(bot.getId(), text, false, 0));
    }
}
