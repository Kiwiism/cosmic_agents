package server.maps;

import client.BotClient;
import client.Character;
import client.Job;
import client.Skill;
import client.SkillFactory;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.keybind.KeyBinding;
import constants.game.GameConstants;
import constants.inventory.ItemConstants;
import constants.skills.Fighter;
import constants.skills.Page;
import constants.skills.Spearman;
import constants.skills.Warrior;
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
import tools.PacketCreator;

import java.awt.*;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BotManager {
    // TODO: list from most important to least important
    // TODO: rework autoAssignSp/getSpPriority/related, implement per level build order, should be backward compatible format(hasn't assigned in many levels), sp should have prompts if multiple options are available (see Hero.java)
    // TODO: Option to ask bot for their current stats/damage range/current buid/inventory
    // TODO: Option to respec bot ap/sp
    // TODO: Option to ask bot to change ap/sp build
    // TODO: Make bot auto scan/autoequip the "best" equipment they have + can equip in their inventory
    // TODO: Option to ask bot to drop their entire equip inventory/only scrolls/only potions/specific item/ etc, some useful options, remember to skip untradable/quest items
    // TODO: some kind of help command/question on available interactions available (can ask to drop, respec, change build, check stats, etc)
    // TODO: Extract/unbloat this file
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
        public int   WAYPOINT_SEEK_X  = 1500;  // expanded rope search radius when setting a waypoint
        public int   WAYPOINT_TIMEOUT = 80;   // ticks before an unreached waypoint expires (~8s)

        // Grind mode
        public int   ATTACK_RANGE_SQ    = 22500; // px² — attack when within ~150px of target monster
        public int   ATTACK_COOLDOWN    = 10;    // ticks between attacks (~800ms at 100ms/tick)
        public int   GRIND_SEEK_RANGE   = 800;   // px; monster search radius
        public int   AOE_MOB_THRESHOLD  = 2;     // nearby mobs needed to prefer AoE skill over single-target
        // TODO: Aoe range/area/hitbox for attack and skill should be read from actual game data
        public long  AOE_RANGE_SQ       = 90000L; // px² AoE sweep radius (~300px)

        // Mob damage taken
        public int   MOB_TOUCH_HALF_W = 60;    // px; approximate half-width of mob bounding box
        public int   MOB_TOUCH_HALF_H = 80;    // px; approximate half-height of mob bounding box
        public int   MOB_HIT_COOLDOWN = 15;    // ticks between mob hits (~1.5s)
        public long  BOT_DEAD_MS      = 60_000L; // ms bot stays dead before respawning

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

    private final Map<Integer, BotEntry> bots = new ConcurrentHashMap<>();

    // TODO: Extract to BotChatManager or something, unbloat this file

    // --- helper prefix used in several info patterns ---
    // matches optional preamble: "what's / what is / tell me / show me / check / how's"
    // followed by "your/ur" — glued before each info keyword
    private static final String INFO_PFX =
            "(?:(?:what.?s?|what\\s+is|tell\\s+me|show\\s+me|check|how.?s?)\\s+)?(?:your|ur)\\s+";

    private static final Pattern FOLLOW_PATTERN = Pattern.compile(
            "\\b(follow(\\s+(me|here|pls|please|now))?|come(\\s+(here|to\\s+me|with\\s+me|closer|on|back))?|"
            + "get\\s+over\\s+here|f\\s+me|(pls|please)\\s+follow)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern STOP_PATTERN = Pattern.compile(
            "\\b(stop(\\s+(moving|it|now|pls|please))?|stay(\\s+(here|there|put))?|"
            + "wait(\\s+(here|up|for\\s+me|a\\s+(sec|moment|bit)))?|"
            + "hold(\\s+(on|up|still|it))?|halt|freeze|don.?t\\s+move|stand\\s+(still|by)|"
            + "chill(\\s+here)?|idle|park(\\s+here)?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final List<String> FOLLOW_REPLIES = List.of(
            "ok", "k", "sure", "omw", "got it", "coming",
            "roger", "yep", "alright",
            "aye", "lets go!", "as you wish", "ok boss");
    private static final List<String> STOP_REPLIES = List.of(
            "ok", "k", "sure", "alright", "got it", "stopping",
            "ok ill wait here", "ill be here", "np", "standing by",
            "understood", "ok boss");

    private static final Pattern GRIND_PATTERN = Pattern.compile(
            "\\b(go\\s+|start\\s+|begin\\s+|let.?s\\s+)?(farm(ing)?|grind(ing)?|hunt(ing)?|train(ing)?)\\b"
            + "|\\b(kill|fight)\\s+(mobs?|monsters?|stuff)\\b"
            + "|\\btime\\s+to\\s+(farm|grind|hunt)\\b"
            + "|\\bgo\\s+get\\s+(exp|xp)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern JOB_SELECT_PATTERN = Pattern.compile(
            "\\b(warrior|fighter|page|spearman|sader|crusader|hero|dk|drk|dark knight|paladin|" +
            "mage|magician|wizard|cleric|healer|fp|il|fp mage|il mage|fp arch|il arch|priest|bishop|" +
            "bowman|bowmen|archer|hunter|crossbow|xbow|sniper|ranger|bowmaster|bm|marksman|mm|" +
            "thief|assassin|sin|bandit|dit|hermit|chief bandit|cb|shadower|shad|night lord|nl|" +
            "pirate|brawler|gunslinger|gun|marauder|outlaw|bucc|buccaneer|corsair|" +
            "white knight|wk|dragon knight)\\b", Pattern.CASE_INSENSITIVE);

    private static final List<String> GRIND_REPLIES = List.of(
            "ok", "on it", "lets get it", "farming time", "got it",
            "sure", "ok boss", "time to grind");
    private static final List<String> DEATH_REPLIES = List.of(
            "oops im dead", "gg", "rip me", "oww", "i died lol",
            "welp", "ouchh", "nooo", "ok i died", "i'll be right back");

    private static final Pattern GREETING_PATTERN = Pattern.compile(
            "\\b(hi+|hey+|hello+|sup|yo+|howdy|hiya|heya|hai|ello|"
            + "whats?\\s*up|waz+up|wassup|hows?\\s+it\\s+going|"
            + "(good\\s+)?(morning|evening|afternoon)|"
            + "how\\s+(are|r)\\s+(you|u|ya)(\\s+doing)?|"
            + "what.?s\\s+(good|up|new|poppin.?))\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern STATS_PATTERN = Pattern.compile(
            INFO_PFX + "(stats?|str(ength)?|dex(terity)?|int(elligence)?|luk|level|lv)\\b"
            + "|\\bwhat\\s+(are|r)\\s+(your|ur)\\s+stats\\b"
            + "|\\bwhat\\s+(level|lv)\\s+(are|r)\\s+(you|u)\\b"
            + "|\\bhow\\s+(strong|tough|good)\\s+(are|r)\\s+(you|u)\\b"
            + "|\\bur\\s+(stats?|level|lv|str|dex|int|luk)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern RANGE_PATTERN = Pattern.compile(
            INFO_PFX + "(range|dmg|damage|dps|min.?max|attack\\s+range)\\b"
            + "|\\bhow\\s+much\\s+(dmg|damage)\\s+(do|can)\\s+(you|u)\\s+(do|deal|hit)\\b"
            + "|\\bhow\\s+hard\\s+(do|can)\\s+(you|u)\\s+hit\\b"
            + "|\\bur\\s+(range|dmg|damage|dps)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern BUILD_PATTERN = Pattern.compile(
            INFO_PFX + "build\\b"
            + "|\\b(current|what.?s\\s+(the|your|ur))\\s+build\\b"
            + "|\\bhow\\s+(much|many)\\s+ap\\s+(do\\s+(you|u)\\s+have|left|remaining)\\b"
            + "|\\b(ap\\s+left|remaining\\s+ap)\\b"
            + "|\\bur\\s+build\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern INVENTORY_PATTERN = Pattern.compile(
            INFO_PFX + "(loot|inv|inventory|items?|equips?|gear|bag|stuff)\\b"
            + "|\\bwhat\\s+(do\\s+(you|u)\\s+have|are\\s+(you|u)\\s+carrying|gear\\s+do\\s+(you|u)\\s+have)\\b"
            + "|\\b(how\\s+many|check\\s+(your|ur))\\s+items?\\b"
            + "|\\bur\\s+(inv|inventory|items?|equips?|gear)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SCROLLS_PATTERN = Pattern.compile(
            INFO_PFX + "scrolls?\\b"
            + "|\\b(any|do\\s+(you|u)\\s+have(\\s+any)?|got(\\s+any)?|"
            + "carrying(\\s+any)?|you\\s+got(\\s+any)?)\\s+scrolls?\\b"
            + "|\\bhow\\s+many\\s+scrolls?\\b"
            + "|\\bscrolls?\\s+on\\s+(you|u|ya)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern AP_PURE_STR_PATTERN = Pattern.compile(
            "\\bpure\\s+str\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern AP_FIXED_DEX_PATTERN = Pattern.compile(
            "\\b(\\d+)\\s*dex\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern AP_CHANGE_BUILD_PATTERN = Pattern.compile(
            "\\b(change|switch|update|reset|new)\\s+(your\\s+|ur\\s+)?build\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LOGOUT_PATTERN = Pattern.compile(
            "\\b((save\\s+and\\s+)?log\\s*(off|out)|disconnect|(pls|please)\\s+log(\\s+me)?\\s+(off|out))\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RELOG_PATTERN = Pattern.compile(
            "\\b(relog|save\\s+and\\s+relog|reconnect|log\\s+back\\s+in)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LOGOUT_CONFIRM_PATTERN = Pattern.compile(
            "\\b(yes|yep|yeah|yea|y|ok|sure|confirm|do\\s+it|go\\s+(ahead|for\\s+it))\\b",
            Pattern.CASE_INSENSITIVE);

    // TODO: Extract Ap/Sp/Job/build related code to BotBuildManager or something, unbloat this file
    /**
     * AP build for a warrior bot.
     * dexTarget = 0 means pure STR (no DEX investment beyond base).
     * dexTarget > 0 means fill DEX to that value first, then all STR.
     */
    private static class ApBuild {
        final int dexTarget;
        ApBuild(int dexTarget) { this.dexTarget = dexTarget; }
    }
    private static final List<String> GREETING_REPLIES = List.of(
            "hey", "hi", "sup", "yo", "heya", "hii", "hey!!", "hi!!");
    private static final List<String> WB_REPLIES = List.of(
            "wb", "wb!", "welcome back", "oh ur back", "hey ur back", "welcome back!");

    private static String randomReply(List<String> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    private void botSay(Character bot, String text) {
        bot.getMap().broadcastMessage(PacketCreator.getChatText(bot.getId(), text, false, 0));
    }

    // TODO: Extract related to movement to BotMovementManager or something, unbloat this file
    // -------------------------------------------------------------------------
    // Entry state
    // -------------------------------------------------------------------------

    private static class BotEntry {
        final Character bot;
        volatile Character owner;
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

        // Skill cache — rebuilt on job change
        int          cachedSkillJob = -1;
        int          attackSkillId  = 0;   // best single-target attack skill (0 = basic attack)
        int          aoeSkillId     = 0;   // best AoE attack skill (0 = none)
        int          aoeSkillMobs   = 1;   // mobCount of the chosen AoE skill
        List<Integer> buffSkillIds  = new ArrayList<>();
        // skillId → ms timestamp when buff should be re-applied (0 = apply immediately)
        final Map<Integer, Long> nextBuffAt = new HashMap<>();

        // Damage taken
        long deadUntil      = 0;  // ms timestamp when bot may respawn; 0 = alive
        int  mobHitCooldown = 0;  // ticks until next mob hit is allowed

        // Loot & potions
        int  potCheckTimer      = 0;
        int  invFullWarnCooldown = 0;

        // Job advancement: 0=none, 8=lv8 mage prompt, 10=lv10 1st job, 30=2nd, 70=3rd, 120=4th
        int jobPromptSent  = 0;
        int lastKnownLevel = -1;

        // AP/SP builds — set once on first job, can be changed via chat
        ApBuild apBuild       = null;   // null = not chosen yet
        boolean apPromptSent  = false;  // prevent re-prompting before owner responds

        // Pending two-step action: null, "logout", or "relog"
        String pendingAction = null;

        // Message queue — sends with ~5s spacing between messages
        final ArrayDeque<String> msgQueue = new ArrayDeque<>();
        boolean msgSending = false;

        // AFK detection
        Point ownerAfkPos     = null;
        long  ownerAfkSinceMs = 0;
        boolean ownerWasAfk   = false;

        // Foothold index, rebuilt on map change
        int lastMapId = -1;
        Map<Integer, Foothold> fhIndex = new HashMap<>();

        BotEntry(Character bot, Character owner, ScheduledFuture<?> task) {
            this.bot = bot;
            this.owner = owner;
            this.task = task;
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void registerBot(int ownerCharId, Character owner, Character bot) {
        BotEntry old = bots.remove(ownerCharId);
        if (old != null) old.task.cancel(false);

        ScheduledFuture<?> task = TimerManager.getInstance().register(
                () -> tick(ownerCharId, bot), cfg.TICK_MS);
        BotEntry entry = new BotEntry(bot, owner, task);
        bots.put(ownerCharId, entry);
        TimerManager.getInstance().schedule(() -> checkBotStatus(entry, bot), 2000);
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

        // Logout / relog — two-step confirmation
        if (RELOG_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
                entry.pendingAction = "relog";
                entry.following = false;
                entry.grinding  = false;
                List<String> prompts = List.of(
                        "relog? say yes to confirm",
                        "save and relog? type yes",
                        "relogging? say yes to go ahead");
                botSay(entry.bot, randomReply(prompts));
            }, 1000);
            return;
        }
        if (LOGOUT_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
                entry.pendingAction = "logout";
                entry.following = false;
                entry.grinding  = false;
                List<String> prompts = List.of(
                        "log off? you sure? say yes to confirm",
                        "save and log off? say yes if you're sure",
                        "logging off? type yes to confirm");
                botSay(entry.bot, randomReply(prompts));
            }, 1000);
            return;
        }
        if (entry.pendingAction != null) {
            if (LOGOUT_CONFIRM_PATTERN.matcher(message).find()) {
                String action = entry.pendingAction;
                entry.pendingAction = null;
                if ("relog".equals(action)) {
                    TimerManager.getInstance().schedule(() -> {
                        botSay(entry.bot, randomReply(List.of("brb!", "relogging~", "one sec, relogging")));
                        int charId      = entry.bot.getId();
                        int ownerCharId = entry.owner.getId();
                        int world       = entry.bot.getClient().getWorld();
                        int channel     = entry.bot.getClient().getChannel();
                        TimerManager.getInstance().schedule(() -> {
                            entry.bot.saveCharToDB(true);
                            entry.bot.getClient().disconnect(false, false);
                            TimerManager.getInstance().schedule(
                                    () -> reloginBot(charId, ownerCharId, world, channel), 10_000);
                        }, 2000);
                    }, 1000);
                } else {
                    TimerManager.getInstance().schedule(() -> {
                        botSay(entry.bot, randomReply(List.of("ok! saving and logging off~", "cya!!", "ok bye!!")));
                        TimerManager.getInstance().schedule(() -> {
                            entry.bot.saveCharToDB(true);
                            entry.bot.getClient().disconnect(false, false);
                        }, 2000);
                    }, 1000);
                }
            } else {
                entry.pendingAction = null;
                TimerManager.getInstance().schedule(() ->
                        botSay(entry.bot, "ok nvm, staying!"), 800);
            }
            return;
        }

        if (FOLLOW_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
                entry.grinding = false;
                botSay(entry.bot, randomReply(FOLLOW_REPLIES));
                TimerManager.getInstance().schedule(() -> entry.following = true, 250);
            }, 1500);
        } else if (GRIND_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
                entry.following = false;
                setupAutopotForBot(entry.bot);
                botSay(entry.bot, grindStartMessage(entry.bot));
                TimerManager.getInstance().schedule(() -> {
                    entry.grinding = true;
                    checkBotStatus(entry, entry.bot);
                }, 250);
            }, 1500);
        } else if (STOP_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
                entry.following = false;
                entry.grinding  = false;
                TimerManager.getInstance().schedule(() -> botSay(entry.bot, randomReply(STOP_REPLIES)), 1500);
            }, 1000);
        } else if (GREETING_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
                queueBotSay(entry, randomReply(GREETING_REPLIES));
                checkBotStatus(entry, entry.bot);
            }, 1000);
        }

        // AP build selection (pure str / fixed dex / change build)
        if (AP_CHANGE_BUILD_PATTERN.matcher(message).find()) {
            entry.apBuild      = null;
            entry.apPromptSent = false;
            String prompt = buildApPrompt(entry, entry.bot);
            if (prompt != null) botSay(entry.bot, prompt);
        } else if (AP_PURE_STR_PATTERN.matcher(message).find()) {
            setApBuild(entry, new ApBuild(0), "pure str it is! dumping everything into str");
        } else {
            Matcher m = AP_FIXED_DEX_PATTERN.matcher(message);
            if (m.find()) {
                int dexTarget = Integer.parseInt(m.group(1));
                setApBuild(entry, new ApBuild(dexTarget),
                        "ok! keeping dex at " + dexTarget + ", rest into str");
            }
        }

        // Info commands
        if (STATS_PATTERN.matcher(message).find())
            TimerManager.getInstance().schedule(() -> reportStats(entry, entry.bot), 1000);
        if (RANGE_PATTERN.matcher(message).find())
            TimerManager.getInstance().schedule(() -> reportRange(entry, entry.bot), 1000);
        if (BUILD_PATTERN.matcher(message).find())
            TimerManager.getInstance().schedule(() -> reportBuild(entry, entry.bot), 1000);
        if (INVENTORY_PATTERN.matcher(message).find())
            TimerManager.getInstance().schedule(() -> reportInventory(entry, entry.bot), 1000);
        if (SCROLLS_PATTERN.matcher(message).find())
            TimerManager.getInstance().schedule(() -> reportScrolls(entry, entry.bot), 1000);

        // Job advancement — check if message contains a valid job selection
        if (JOB_SELECT_PATTERN.matcher(message).find()) {
            Job advJob = resolveJobChange(entry.bot, message.toLowerCase());
            if (advJob != null) {
                String jobName = jobDisplayName(advJob);
                List<String> replies = List.of(
                        "ok, ill change to " + jobName + "!",
                        "alright becoming a " + jobName + " then",
                        "ok " + jobName + " it is!",
                        "sure, going " + jobName,
                        "ok changing to " + jobName + "...");
                botSay(entry.bot, randomReply(replies));
                TimerManager.getInstance().schedule(() -> entry.bot.changeJob(advJob), 1000);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Main tick
    // -------------------------------------------------------------------------

    private void tick(int ownerCharId, Character bot) {
        BotEntry entry = bots.get(ownerCharId);
        if (entry == null) return;

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
            resetEntryState(entry);
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
        tickPotionCheck(entry, bot, owner);
        checkLevelUp(entry, bot);
        tickAfkCheck(entry, owner);
        rebuildSkillCacheIfNeeded(entry, bot);
        tickBuffs(entry, bot);

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
                bot.setStance(cfg.STAND_STANCE); // ensure spawn packet shows stand stance, not walk
                bot.changeMap(owner.getMap(), spawn);
                resetEntryState(entry);
                return;
            }
        }
        // Teleport if hopelessly far — applies to both follow and grind (catches falling off map)
        if (Math.abs(botPos.x - targetPos.x) + Math.abs(botPos.y - targetPos.y) > cfg.TELEPORT_DIST) {
            Point spawn = new Point(targetPos.x, targetPos.y - 10);
            bot.setPosition(spawn);
            resetEntryState(entry);
            broadcastMovement(bot, 0, 0);
            return;
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
//                log.debug("[GRIND] no target found for bot={} map={}", bot.getName(), bot.getMapId());
                if (entry.inAir) tickAirborne(entry, targetPos);
                else { bot.setStance(5); broadcastMovement(bot, 0, 0); }
                return;
            }
            entry.grindTarget = target;
            int distSq = (int) target.getPosition().distanceSq(botPos);
//            log.debug("[GRIND] target={} distSq={} rangeSq={} inAir={} climbing={}",
//                    target.getId(), distSq, cfg.ATTACK_RANGE_SQ, entry.inAir, entry.climbing);
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
                    && arcCheckJump(bot, botPos, stepX, targetPos.x, targetPos.y)) {
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

    private static long distSq(Point a, Point b) {
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
    private void applyMobHit(BotEntry entry, Character bot, Monster mob) {
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

        // Knock bot back away from mob
        Point bp     = bot.getPosition();
        int   kbX    = bp.x + (dir == 0 ? 30 : -30);
        Point snapped = bot.getMap().getPointBelow(new Point(kbX, bp.y));
        bot.setPosition(snapped != null ? snapped : new Point(kbX, bp.y));
        broadcastMovement(bot, 0, 0);

        entry.mobHitCooldown = cfg.MOB_HIT_COOLDOWN;

        if (bot.getHp() <= 0) {
            bot.setStance(cfg.DEAD_STANCE);
            broadcastMovement(bot, 0, 0);
            botSay(bot, randomReply(DEATH_REPLIES));
            entry.deadUntil = System.currentTimeMillis() + cfg.BOT_DEAD_MS;
            resetEntryState(entry);
        }
    }

    private void reloginBot(int charId, int ownerCharId, int world, int channel) {
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
        resetEntryState(entry);
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
    private int[] countPotions(Character bot) {
        int hp = 0, mp = 0;
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        for (Item item : bot.getInventory(InventoryType.USE).list()) {
            StatEffect eff = ii.getItemEffect(item.getItemId());
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
    private void setupAutopotForBot(Character bot) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        int hpItemId = -1, mpItemId = -1;
        int bestHp = 0, bestMp = 0;
        for (Item item : bot.getInventory(InventoryType.USE).list()) {
            if (item.getQuantity() <= 0) continue;
            StatEffect eff = ii.getItemEffect(item.getItemId());
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

    /** Builds grind-start reply with low-potion warning when below POT_LOW_WARN. */
    private String grindStartMessage(Character bot) {
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
    private void tickPotionCheck(BotEntry entry, Character bot, Character owner) {
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

    /**
     * Simulates the jump arc from {@code from} stepping {@code stepX} per tick.
     * Returns true if the bot would land on a foothold meaningfully higher than
     * its current position (gaining at least JUMP_Y_THRESH px of height).
     *
     * Uses the same prevY→newY crossing pattern as tickAirborne's landing check
     * so platforms are never missed due to arc position quantisation.
     */
    private boolean arcCheckJump(Character bot, Point from, int stepX, int targetX, int targetY) {
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

    // -------------------------------------------------------------------------
    // Job advancement helpers
    // -------------------------------------------------------------------------

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

    // Status check — called on spawn, grind start, greeting, and level-up
    // -------------------------------------------------------------------------

    private void checkBotStatus(BotEntry entry, Character bot) {
        String jobPrompt = buildJobPrompt(entry, bot);
        if (jobPrompt != null) queueBotSay(entry, jobPrompt);
        String apPrompt = buildApPrompt(entry, bot);
        if (apPrompt != null) queueBotSay(entry, apPrompt);
    }

    private void reportStats(BotEntry entry, Character bot) {
        queueBotSay(entry, String.format("lv%d %s | str %d dex %d int %d luk %d | hp %d/%d mp %d/%d",
                bot.getLevel(), jobDisplayName(bot.getJob()),
                bot.getStr(), bot.getDex(), bot.getInt(), bot.getLuk(),
                bot.getHp(), bot.getCurrentMaxHp(),
                bot.getMp(), bot.getCurrentMaxMp()));
    }

    private void reportRange(BotEntry entry, Character bot) {
        int watk   = bot.getTotalWatk();
        int maxDmg = Math.max(1, bot.calculateMaxBaseDamage(watk));
        int minDmg = Math.max(1, bot.calculateMinBaseDamage(watk));
        queueBotSay(entry, String.format("my dmg is %d-%d, watk %d", minDmg, maxDmg, watk));
    }

    private void reportBuild(BotEntry entry, Character bot) {
        queueBotSay(entry, String.format("build: str %d / dex %d / int %d / luk %d, %d ap left",
                bot.getStr(), bot.getDex(), bot.getInt(), bot.getLuk(),
                bot.getRemainingAp()));
    }

    private void reportInventory(BotEntry entry, Character bot) {
        int slots = bot.getInventory(InventoryType.EQUIP).getSlotLimit();
        int count = slots - bot.getInventory(InventoryType.EQUIP).getNumFreeSlot();
        queueBotSay(entry, "I have " + count + " item" + (count != 1 ? "s" : "") + " in my equip inventory");
    }

    private void reportScrolls(BotEntry entry, Character bot) {
        int count = 0;
        for (Item item : bot.getInventory(InventoryType.USE).list()) {
            int id = item.getItemId();
            if (id >= 2040000 && id < 2050000) count += item.getQuantity();
        }
        queueBotSay(entry, count > 0
                ? "I have " + count + " scroll" + (count != 1 ? "s" : "") + " on me"
                : "no scrolls on me");
    }

    /** Returns the next job-advancement prompt (updating jobPromptSent), or null if none pending. */
    private String buildJobPrompt(BotEntry entry, Character bot) {
        int lvl = bot.getLevel();
        Job job = bot.getJob();
        int prompted = entry.jobPromptSent;

        if (job == Job.BEGINNER) {
            if (lvl >= 10 && prompted < 10) {
                entry.jobPromptSent = 10;
                return "hey i can change jobs now!! warrior, mage, bowman, thief, or pirate?";
            } else if (lvl >= 8 && prompted < 8) {
                entry.jobPromptSent = 8;
                return "i can become a mage already if u want, or wait til lv10 for other jobs";
            }
            return null;
        }

        if (lvl >= 30 && prompted < 30) {
            String msg = switch (job) {
                case WARRIOR  -> "lv30! 2nd job time~ fighter, page, or spearman?";
                case MAGICIAN -> "lv30! pick 2nd job: f/p wizard, i/l wizard, or cleric?";
                case BOWMAN   -> "lv30! hunter or crossbowman?";
                case THIEF    -> "lv30! assassin or bandit?";
                case PIRATE   -> "lv30! brawler or gunslinger?";
                default       -> null;
            };
            if (msg != null) { entry.jobPromptSent = 30; return msg; }
        }

        if (lvl >= 70 && prompted < 70) {
            String msg = switch (job) {
                case FIGHTER     -> "lv70!! 3rd job, type 'crusader'";
                case PAGE        -> "lv70!! type 'white knight' or 'wk'";
                case SPEARMAN    -> "lv70!! type 'dragon knight' or 'dk'";
                case FP_WIZARD   -> "lv70!! type 'fp mage'";
                case IL_WIZARD   -> "lv70!! type 'il mage'";
                case CLERIC      -> "lv70!! type 'priest'";
                case HUNTER      -> "lv70!! type 'ranger'";
                case CROSSBOWMAN -> "lv70!! type 'sniper'";
                case ASSASSIN    -> "lv70!! type 'hermit'";
                case BANDIT      -> "lv70!! type 'chief bandit' or 'cb'";
                case BRAWLER     -> "lv70!! type 'marauder'";
                case GUNSLINGER  -> "lv70!! type 'outlaw'";
                default          -> null;
            };
            if (msg != null) { entry.jobPromptSent = 70; return msg; }
        }

        if (lvl >= 120 && prompted < 120) {
            String msg = switch (job) {
                case CRUSADER     -> "lv120!! type 'hero' for 4th job!!";
                case WHITEKNIGHT  -> "lv120!! type 'paladin'";
                case DRAGONKNIGHT -> "lv120!! type 'dark knight' or 'drk'";
                case FP_MAGE      -> "lv120!! type 'fp archmage' or 'fp arch'";
                case IL_MAGE      -> "lv120!! type 'il archmage' or 'il arch'";
                case PRIEST       -> "lv120!! type 'bishop'";
                case RANGER       -> "lv120!! type 'bowmaster' or 'bm'";
                case SNIPER       -> "lv120!! type 'marksman' or 'mm'";
                case HERMIT       -> "lv120!! type 'night lord' or 'nl'";
                case CHIEFBANDIT  -> "lv120!! type 'shadower'";
                case MARAUDER     -> "lv120!! type 'buccaneer' or 'bucc'";
                case OUTLAW       -> "lv120!! type 'corsair'";
                default           -> null;
            };
            if (msg != null) { entry.jobPromptSent = 120; return msg; }
        }

        return null;
    }

    /** Detects level-up; auto-assigns SP and AP, and at job-advancement levels triggers a status check. */
    private void checkLevelUp(BotEntry entry, Character bot) {
        int lvl = bot.getLevel();
        if (entry.lastKnownLevel == lvl) return;
        int prev = entry.lastKnownLevel;
        entry.lastKnownLevel = lvl;
        if (prev == -1) return;  // initial sync on first tick

        autoAssignSp(bot);
        autoAssignAp(entry, bot);

        if (lvl == 8 || lvl == 10 || lvl == 30 || lvl == 70 || lvl == 120) {
            entry.grinding  = false;
            entry.following = true;
            checkBotStatus(entry, bot);
        }
    }

    /** Detects owner AFK (same position ≥5 min) and says "wb" when they return. */
    private void tickAfkCheck(BotEntry entry, Character owner) {
        Point pos = owner.getPosition();
        long now  = System.currentTimeMillis();

        if (entry.ownerAfkPos == null) {
            entry.ownerAfkPos     = pos;
            entry.ownerAfkSinceMs = now;
            return;
        }

        if (!pos.equals(entry.ownerAfkPos)) {
            if (entry.ownerWasAfk) {
                entry.ownerWasAfk = false;
                final Character bot = entry.bot;
                TimerManager.getInstance().schedule(
                        () -> botSay(bot, randomReply(WB_REPLIES)), 2000);
            }
            entry.ownerAfkPos     = pos;
            entry.ownerAfkSinceMs = now;
        } else if (!entry.ownerWasAfk && (now - entry.ownerAfkSinceMs) >= 5 * 60_000L) {
            entry.ownerWasAfk = true;
        }
    }

    // -------------------------------------------------------------------------
    // AP / SP auto-assignment
    // -------------------------------------------------------------------------

    /** Stores the AP build, confirms it to the owner, and immediately spends any pending AP. */
    private void setApBuild(BotEntry entry, ApBuild build, String confirmMsg) {
        entry.apBuild      = build;
        entry.apPromptSent = false;
        botSay(entry.bot, confirmMsg);
        autoAssignAp(entry, entry.bot);
    }

    /**
     * Returns a prompt asking the owner to choose an AP build, or null if:
     * - no AP is pending, - build already chosen, - prompt already sent, or
     * - job is not a supported warrior branch.
     */
    private String buildApPrompt(BotEntry entry, Character bot) {
        Job job = bot.getJob();
        if (job != Job.WARRIOR && job != Job.FIGHTER && job != Job.PAGE && job != Job.SPEARMAN) return null;
        if (entry.apBuild != null || entry.apPromptSent || bot.getRemainingAp() < 1) return null;
        entry.apPromptSent = true;
        return "what AP build? type 'pure str' or e.g. '25 dex' to set a dex target";
    }

    /** Spends all remaining AP according to the stored build (STR primary, DEX up to target). */
    private void autoAssignAp(BotEntry entry, Character bot) {
        if (entry.apBuild == null || bot.getRemainingAp() < 1) return;
        int ap = bot.getRemainingAp();
        int strGain = 0, dexGain = 0;
        if (entry.apBuild.dexTarget > 0) {
            int dexNeeded = Math.max(0, entry.apBuild.dexTarget - bot.getDex());
            dexGain = Math.min(dexNeeded, ap);
            ap -= dexGain;
        }
        strGain = ap;
        if (strGain > 0 || dexGain > 0) {
            bot.assignStrDexIntLuk(strGain, dexGain, 0, 0);
        }
    }

    /**
     * Spends all available SP for the bot's current job using the hardcoded priority order.
     * Skills are leveled from highest priority (index 0) downward; stops when all SP is spent.
     */
    private void autoAssignSp(Character bot) {
        int[] priority = getSpPriority(bot.getJob());
        if (priority == null) return;
        boolean spent;
        do {
            spent = false;
            for (int skillId : priority) {
                Skill skill = SkillFactory.getSkill(skillId);
                if (skill == null) continue;
                int book = GameConstants.getSkillBook(skillId / 10000);
                if (bot.getRemainingSps()[book] < 1) break;
                int curLevel = bot.getSkillLevel(skill);
                if (curLevel < skill.getMaxLevel()) {
                    bot.gainSp(-1, book, false);
                    bot.changeSkillLevel(skill, (byte) (curLevel + 1),
                            bot.getMasterLevel(skill), bot.getSkillExpiration(skill));
                    spent = true;
                    break;
                }
            }
        } while (spent);
    }

    private static int[] getSpPriority(Job job) {
        // TODO: deprecate this, very important, any LLM working on this should take priority to refactor before proceeding
        // TODO: look at Warrior.java, make presets for each Job at their respective class instead to not clog here, make it like job.getSpPriority() or something, refer information from constant classes as much as possible
        // TODO: SP assignment path should be more detailed for each level like in Warrior.java for earlier access to mobbing skills etc, current implementation of max 1 skill at a time is not good enough
        return switch (job) {
            // 1st job: HP Recovery first (early sustain), then MaxHP%, then attacks
            case WARRIOR -> new int[]{
                Warrior.IMPROVED_HPREC,  // max 16 — passive regen, improves early survivability
                Warrior.IMPROVED_MAXHP,  // max 10 — passive MaxHP%
                Warrior.POWER_STRIKE,    // max 20 — single target
                Warrior.SLASH_BLAST,     // max 20 — AoE
                Warrior.IRON_BODY,       // max 20 — DEF buff, filler
                Warrior.ENDURE,          // max 16 — passive regen while moving, low priority
            };
            // Fighter → Hero: Mastery first for accuracy, Rage for party ATK, skip Final Attack
            // (FA procs spread Slash Blast damage across targets, reducing DPS — community consensus)
            case FIGHTER -> new int[]{
                Fighter.SWORD_MASTERY,      // max 20 — accuracy + min damage
                Fighter.RAGE,               // max 20 — party ATK buff
                Fighter.POWER_GUARD,        // max 30 — damage reflect
                Fighter.SWORD_BOOSTER,      // max 20 — attack speed
                Fighter.FINAL_ATTACK_SWORD, // max 30 — last: FA hurts multi-mob DPS
                Fighter.AXE_MASTERY,        // filler if using axe
                Fighter.AXE_BOOSTER,
                Fighter.FINAL_ATTACK_AXE,
            };
            // Page → Paladin/White Knight: Mastery, Threaten debuff, Power Guard, Booster
            case PAGE -> new int[]{
                Page.SWORD_MASTERY,      // max 20
                Page.THREATEN,           // max 20 — enemy DEF debuff, useful for bossing
                Page.POWER_GUARD,        // max 30
                Page.SWORD_BOOSTER,      // max 20
                Page.FINAL_ATTACK_SWORD, // last
                Page.BW_MASTERY,
                Page.BW_BOOSTER,
                Page.FINAL_ATTACK_BW,
            };
            // Spearman → Dark Knight: Hyper Body first — the most valuable party skill in the game
            case SPEARMAN -> new int[]{
                Spearman.HYPER_BODY,          // max 30 — MaxHP/MP%, top party skill
                Spearman.SPEAR_MASTERY,       // max 20
                Spearman.IRON_WILL,           // max 20 — party HP buff
                Spearman.SPEAR_BOOSTER,       // max 20
                Spearman.FINAL_ATTACK_SPEAR,  // last
                Spearman.POLEARM_MASTERY,
                Spearman.POLEARM_BOOSTER,
                Spearman.FINAL_ATTACK_POLEARM,
            };
            default -> null;
        };
    }

    // -------------------------------------------------------------------------
    // Message queue — 5-second spacing between consecutive bot messages
    // -------------------------------------------------------------------------

    private void queueBotSay(BotEntry entry, String message) {
        synchronized (entry.msgQueue) {
            entry.msgQueue.add(message);
            if (!entry.msgSending) {
                entry.msgSending = true;
                drainMsgQueue(entry);
            }
        }
    }

    private void drainMsgQueue(BotEntry entry) {
        String msg;
        synchronized (entry.msgQueue) {
            msg = entry.msgQueue.poll();
            if (msg == null) { entry.msgSending = false; return; }
        }
        botSay(entry.bot, msg);
        TimerManager.getInstance().schedule(() -> drainMsgQueue(entry), 5000);
    }

    /** Maps a chat keyword to the correct next Job given bot's current job and level. Returns null if not valid. */
    private static Job resolveJobChange(Character bot, String msg) {
        Job cur = bot.getJob();
        int lvl = bot.getLevel();

        return switch (cur) {
            case BEGINNER -> {
                if (lvl >= 8  && msg.matches(".*\\b(mage|magician|wizard|cleric|healer|fp|il|fp mage|il mage)\\b.*")) yield Job.MAGICIAN;
                if (lvl >= 10 && msg.matches(".*\\b(warrior|fighter|page|spearman|sader)\\b.*")) yield Job.WARRIOR;
                if (lvl >= 10 && msg.matches(".*\\b(bowman|bowmen|archer|hunter|crossbow|xbow)\\b.*")) yield Job.BOWMAN;
                if (lvl >= 10 && msg.matches(".*\\b(thief|assassin|sin|bandit|dit)\\b.*")) yield Job.THIEF;
                if (lvl >= 10 && msg.matches(".*\\b(pirate|brawler|gunslinger|gun|bucc)\\b.*")) yield Job.PIRATE;
                yield null;
            }
            // 2nd job
            case WARRIOR -> lvl < 30 ? null :
                    msg.matches(".*\\b(fighter|sader)\\b.*") ? Job.FIGHTER :
                    msg.matches(".*\\bpage\\b.*") ? Job.PAGE :
                    msg.matches(".*\\b(spearman|spear)\\b.*") ? Job.SPEARMAN : null;
            case MAGICIAN -> lvl < 30 ? null :
                    msg.matches(".*\\b(fp|fp wizard|fp mage|fire|f\\.p)\\b.*") ? Job.FP_WIZARD :
                    msg.matches(".*\\b(il|il wizard|il mage|ice|i\\.l)\\b.*") ? Job.IL_WIZARD :
                    msg.matches(".*\\b(cleric|healer|priest|bishop)\\b.*") ? Job.CLERIC : null;
            case BOWMAN -> lvl < 30 ? null :
                    msg.matches(".*\\b(hunter|bow)\\b.*") ? Job.HUNTER :
                    msg.matches(".*\\b(crossbow|xbow|crossbowman)\\b.*") ? Job.CROSSBOWMAN : null;
            case THIEF -> lvl < 30 ? null :
                    msg.matches(".*\\b(assassin|sin)\\b.*") ? Job.ASSASSIN :
                    msg.matches(".*\\b(bandit|dit)\\b.*") ? Job.BANDIT : null;
            case PIRATE -> lvl < 30 ? null :
                    msg.matches(".*\\b(brawler|knuckle)\\b.*") ? Job.BRAWLER :
                    msg.matches(".*\\b(gunslinger|gun)\\b.*") ? Job.GUNSLINGER : null;
            // 3rd job
            case FIGHTER     -> lvl >= 70 && msg.matches(".*\\bcrusader\\b.*")                 ? Job.CRUSADER     : null;
            case PAGE        -> lvl >= 70 && msg.matches(".*\\b(white knight|wk)\\b.*")         ? Job.WHITEKNIGHT  : null;
            case SPEARMAN    -> lvl >= 70 && msg.matches(".*\\b(dragon knight|dk)\\b.*")        ? Job.DRAGONKNIGHT : null;
            case FP_WIZARD   -> lvl >= 70 && msg.matches(".*\\b(fp mage|fp)\\b.*")              ? Job.FP_MAGE      : null;
            case IL_WIZARD   -> lvl >= 70 && msg.matches(".*\\b(il mage|il)\\b.*")              ? Job.IL_MAGE      : null;
            case CLERIC      -> lvl >= 70 && msg.matches(".*\\bpriest\\b.*")                    ? Job.PRIEST       : null;
            case HUNTER      -> lvl >= 70 && msg.matches(".*\\branger\\b.*")                    ? Job.RANGER       : null;
            case CROSSBOWMAN -> lvl >= 70 && msg.matches(".*\\bsniper\\b.*")                    ? Job.SNIPER       : null;
            case ASSASSIN    -> lvl >= 70 && msg.matches(".*\\bhermit\\b.*")                    ? Job.HERMIT       : null;
            case BANDIT      -> lvl >= 70 && msg.matches(".*\\b(chief bandit|cb|chief)\\b.*")   ? Job.CHIEFBANDIT  : null;
            case BRAWLER     -> lvl >= 70 && msg.matches(".*\\bmarauder\\b.*")                  ? Job.MARAUDER     : null;
            case GUNSLINGER  -> lvl >= 70 && msg.matches(".*\\boutlaw\\b.*")                    ? Job.OUTLAW       : null;
            // 4th job
            case CRUSADER    -> lvl >= 120 && msg.matches(".*\\bhero\\b.*")                         ? Job.HERO        : null;
            case WHITEKNIGHT -> lvl >= 120 && msg.matches(".*\\bpaladin\\b.*")                      ? Job.PALADIN     : null;
            case DRAGONKNIGHT -> lvl >= 120 && msg.matches(".*\\b(dark knight|drk)\\b.*")           ? Job.DARKKNIGHT  : null;
            case FP_MAGE     -> lvl >= 120 && msg.matches(".*\\b(fp archmage|fp arch)\\b.*")        ? Job.FP_ARCHMAGE : null;
            case IL_MAGE     -> lvl >= 120 && msg.matches(".*\\b(il archmage|il arch)\\b.*")        ? Job.IL_ARCHMAGE : null;
            case PRIEST      -> lvl >= 120 && msg.matches(".*\\bbishop\\b.*")                       ? Job.BISHOP      : null;
            case RANGER      -> lvl >= 120 && msg.matches(".*\\b(bowmaster|bm)\\b.*")               ? Job.BOWMASTER   : null;
            case SNIPER      -> lvl >= 120 && msg.matches(".*\\b(marksman|mm)\\b.*")                ? Job.MARKSMAN    : null;
            case HERMIT      -> lvl >= 120 && msg.matches(".*\\b(night lord|nl)\\b.*")              ? Job.NIGHTLORD   : null;
            case CHIEFBANDIT -> lvl >= 120 && msg.matches(".*\\b(shadower|shad)\\b.*")              ? Job.SHADOWER    : null;
            case MARAUDER    -> lvl >= 120 && msg.matches(".*\\b(buccaneer|bucc)\\b.*")             ? Job.BUCCANEER   : null;
            case OUTLAW      -> lvl >= 120 && msg.matches(".*\\bcorsair\\b.*")                      ? Job.CORSAIR     : null;
            default -> null;
        };
    }

    private static String jobDisplayName(Job job) {
        return switch (job) {
            case WARRIOR     -> "warrior";      case MAGICIAN    -> "mage";
            case BOWMAN      -> "bowman";       case THIEF       -> "thief";
            case PIRATE      -> "pirate";       case FIGHTER     -> "fighter";
            case PAGE        -> "page";         case SPEARMAN    -> "spearman";
            case FP_WIZARD   -> "f/p wizard";   case IL_WIZARD   -> "i/l wizard";
            case CLERIC      -> "cleric";       case HUNTER      -> "hunter";
            case CROSSBOWMAN -> "crossbowman";  case ASSASSIN    -> "assassin";
            case BANDIT      -> "bandit";       case BRAWLER     -> "brawler";
            case GUNSLINGER  -> "gunslinger";   case CRUSADER    -> "crusader";
            case WHITEKNIGHT -> "white knight"; case DRAGONKNIGHT-> "dragon knight";
            case FP_MAGE     -> "f/p mage";     case IL_MAGE     -> "i/l mage";
            case PRIEST      -> "priest";       case RANGER      -> "ranger";
            case SNIPER      -> "sniper";       case HERMIT      -> "hermit";
            case CHIEFBANDIT -> "chief bandit"; case MARAUDER    -> "marauder";
            case OUTLAW      -> "outlaw";       case HERO        -> "hero";
            case PALADIN     -> "paladin";      case DARKKNIGHT  -> "dark knight";
            case FP_ARCHMAGE -> "f/p archmage"; case IL_ARCHMAGE -> "i/l archmage";
            case BISHOP      -> "bishop";       case BOWMASTER   -> "bowmaster";
            case MARKSMAN    -> "marksman";     case NIGHTLORD   -> "night lord";
            case SHADOWER    -> "shadower";     case BUCCANEER   -> "buccaneer";
            case CORSAIR     -> "corsair";
            default -> job.name().toLowerCase();
        };
    }

    private static Map<Integer, Foothold> buildFhIndex(MapleMap map) {
        Map<Integer, Foothold> index = new HashMap<>();
        for (Foothold fh : map.getFootholds().getAllFootholds()) {
            index.put(fh.getId(), fh);
        }
        return index;
    }
}
