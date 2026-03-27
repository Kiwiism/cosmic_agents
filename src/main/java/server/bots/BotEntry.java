package server.bots;

import client.Character;
import client.inventory.Item;
import server.life.Monster;
import server.maps.Foothold;
import server.maps.Rope;

import java.awt.*;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;

class BotEntry {
    final Character bot;
    volatile Character owner;
    volatile boolean following = false;
    final ScheduledFuture<?> task;

    // Physics
    float  velY  = 0f;
    double hspeed = 0.0;   // horizontal velocity in px per 8 ms client physics step
    double physX  = 0.0;   // sub-pixel accumulated X position
    double physY  = 0.0;   // sub-pixel accumulated Y position
    double groundPhysicsCarryMs = 0.0;
    boolean inAir = false;
    int jumpCooldownMs = 0;

    // Rope climbing
    boolean climbing  = false;
    Rope    climbRope = null;

    // Jitter prevention — only starts moving toward owner once distance exceeds FOLLOW_DIST
    boolean wasMovingX = false;

    // Committed horizontal step while airborne — set at jump time, not re-computed each tick
    int airVelX = 0;

    // Rope state
    boolean seekingRope     = false; // only grab a rope mid-air when we intentionally jumped for one
    int     ropeGrabCooldownMs = 0;  // ms before rope-grab is re-enabled after leaving a rope

    // Down-jump: true when prone was shown last tick, jump fires this tick
    boolean downJumpPending = false;
    long downJumpGracePeriodMS = 0;

    // Stuck recovery
    int   stuckCheckElapsedMs = 0;
    Point lastStuckCheckPos   = null;
    int   rawChaseMs          = 0;

    // Waypoint — navigate to a rope outside normal detection range
    Rope  waypointRope  = null;
    int   waypointTimerMs = 0;

    // Grind mode
    volatile boolean grinding = false;
    Monster grindTarget    = null;
    int     attackCooldownMs = 0;

    // Skill cache — rebuilt on job or level change
    int          cachedSkillJob   = -1;
    int          cachedSkillLevel = -1;
    int          attackSkillId  = 0;   // best single-target attack skill (0 = basic attack)
    int          aoeSkillId     = 0;   // best AoE attack skill (0 = none)
    int          aoeSkillMobs   = 1;   // mobCount of the chosen AoE skill
    List<Integer> buffSkillIds  = new ArrayList<>();
    // skillId → ms timestamp when buff should be re-applied (0 = apply immediately)
    final Map<Integer, Long> nextBuffAt = new HashMap<>();

    // Damage taken
    long deadUntil      = 0;  // ms timestamp when bot may respawn; 0 = alive
    int  mobHitCooldownMs = 0;

    // Loot & potions
    int  potCheckTimerMs      = 0;
    int  mpRecoveryTimerMs    = 0;
    int  invFullWarnCooldownMs = 0;

    // Job advancement: 0=none, 8=lv8 mage prompt, 10=lv10 1st job, 30=2nd, 70=3rd, 120=4th
    int jobPromptSent  = 0;
    int lastKnownLevel = -1;

    // AP/SP builds — set once on first job, can be changed via chat
    BotBuildManager.ApBuild apBuild         = null;  // null = not chosen yet
    boolean apPromptSent    = false; // prevent re-prompting before owner responds

    // SP build variant — for jobs with multiple paths (currently only Hero: "1h" or "2h")
    String  spVariant           = null;  // null = use default; set after owner responds
    boolean spVariantPromptSent = false; // sent once; Hero SP held until owner responds

    // Pending two-step action: null, "logout", "relog", or "item_choice"
    String pendingAction       = null;
    String pendingDropCategory = null; // set when pendingAction="item_choice": "scrolls","pots","equips","etc","name:<x>"
    int    lootInhibitMs       = 0;    // prevents bot re-looting its own drops after a give/drop command

    // Trade queue — driven by BotDropManager.tickTrade; null category = idle
    String     pendingTradeCategory = null;  // category being traded across the whole sequence
    List<Item> pendingTradeItems    = null;  // current batch (≤9); null while pausing between trades
    int        pendingTradeMeso     = 0;     // mesos to add to the current trade window
    int        pendingTradeIdx      = 0;     // next item index in current batch
    int        pendingTradeTimerMs  = 0;     // context-dependent timer (reset on state change)
    boolean    pendingTradeMesoAdded = false; // mesos already added to the current trade window
    boolean    pendingTradeAllAdded = false; // all items in batch added; waiting for owner OK
    boolean    pendingTradeBotDone  = false; // bot has called completeTrade this batch
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

    // Human-like spacing: random horizontal offset so multiple bots don't stack on top of each other
    final int followOffsetX = ThreadLocalRandom.current().nextInt(-100, 101);
    // Staggered tick start: skip the first few hundred ms so bots don't all move in lockstep
    int skipDelayMs = ThreadLocalRandom.current().nextInt(0, 501);
    int aiTickAccumulatorMs = 0;

    // Cached AI decisions — updated on logic tick, applied every movement tick
    int     lastDesiredDirection = 0;     // -1 left, 0 stop/idle, 1 right
    boolean climbIdle            = false; // true when holding position on rope
    Point   navTargetPos         = null;
    BotNavigationGraph.Edge navEdge = null;
    int navTargetRegionId = -1;
    boolean navPreciseTarget = false;
    boolean debugPromptSent = false;

    BotEntry(Character bot, Character owner, ScheduledFuture<?> task) {
        this.bot = bot;
        this.owner = owner;
        this.task = task;
    }
}
