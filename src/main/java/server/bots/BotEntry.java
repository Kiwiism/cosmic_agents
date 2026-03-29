package server.bots;

import client.Character;
import client.inventory.Item;
import server.life.Monster;
import server.maps.Foothold;
import server.maps.Rope;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
    float velY = 0f;
    double hspeed = 0.0;
    double physX = 0.0;
    double physY = 0.0;
    double groundPhysicsCarryMs = 0.0;
    boolean inAir = false;
    int jumpCooldownMs = 0;
    int movementVelX = 0;
    int movementVelY = 0;
    int facingDir = 1;
    boolean crouching = false;

    // Rope climbing
    boolean climbing = false;
    Rope climbRope = null;

    // Horizontal movement hysteresis
    boolean wasMovingX = false;

    // Committed horizontal step while airborne
    int airVelX = 0;

    // Movement intent
    boolean climbUpIntent = false;
    int ropeGrabCooldownMs = 0;

    // Down-jump: true when crouch was shown last tick, jump fires this tick
    boolean downJumpPending = false;
    long downJumpGracePeriodMS = 0;

    // Grind mode
    volatile boolean grinding = false;
    Monster grindTarget = null;
    int attackCooldownMs = 0;

    // Skill cache
    int cachedSkillJob = -1;
    int cachedSkillLevel = -1;
    int attackSkillId = 0;
    int aoeSkillId = 0;
    int aoeSkillMobs = 1;
    int healSkillId = 0;
    List<Integer> buffSkillIds = new ArrayList<>();
    final Map<Integer, Long> nextBuffAt = new HashMap<>();
    final Map<Integer, Long> nextSupportBuffAt = new HashMap<>();
    long nextSupportHealAt = 0L;
    boolean supportBuffsEnabled = true;
    boolean supportHealsEnabled = true;

    // Damage taken
    long deadUntil = 0;
    int mobHitCooldownMs = 0;

    // Loot and potions
    int potCheckTimerMs = 0;
    int mpRecoveryTimerMs = 0;
    int invFullWarnCooldownMs = 0;

    // Job advancement prompts
    int jobPromptSent = 0;
    int lastKnownLevel = -1;

    // AP/SP builds
    BotBuildManager.ApBuild apBuild = null;
    boolean apPromptSent = false;
    String spVariant = null;
    boolean spVariantPromptSent = false;

    // Pending two-step action
    String pendingAction = null;
    String pendingDropCategory = null;
    Item pendingLootOfferItem = null;
    int pendingLootOfferRecipientId = 0;
    long pendingLootOfferExpiresAt = 0L;
    int lootInhibitMs = 0;

    // Trade queue
    String pendingTradeCategory = null;
    List<Item> pendingTradeItems = null;
    int pendingTradeRecipientId = 0;
    int pendingTradeMeso = 0;
    int pendingTradeIdx = 0;
    int pendingTradeTimerMs = 0;
    boolean pendingTradeMesoAdded = false;
    boolean pendingTradeAllAdded = false;
    boolean pendingTradeBotDone = false;
    boolean pendingTradeSingleBatch = false;

    // Message queue
    final ArrayDeque<String> msgQueue = new ArrayDeque<>();
    boolean msgSending = false;

    // AFK detection
    Point ownerAfkPos = null;
    long ownerAfkSinceMs = 0;
    boolean ownerWasAfk = false;

    // Foothold index, rebuilt on map change
    int lastMapId = -1;
    Map<Integer, Foothold> fhIndex = new HashMap<>();

    // Human-like spacing and stagger
    final int followOffsetX = ThreadLocalRandom.current().nextInt(-100, 101);
    int skipDelayMs = ThreadLocalRandom.current().nextInt(0, 501);
    int aiTickAccumulatorMs = 0;

    // Cached movement state shared across ticks
    int lastDesiredDirection = 0;
    Point navTargetPos = null;
    BotNavigationGraph.Edge navEdge = null;
    int navTargetRegionId = -1;
    boolean navPreciseTarget = false;
    boolean debugPromptSent = false;
    long nextGearSuggestionAt = 0L;
    long pendingGearPromptAt = 0L;

    // Movement packet cache so repeated no-op packets are suppressed
    boolean movementBroadcastValid = false;
    int lastBroadcastX = 0;
    int lastBroadcastY = 0;
    int lastBroadcastVelX = 0;
    int lastBroadcastVelY = 0;
    int lastBroadcastStance = 0;

    BotEntry(Character bot, Character owner, ScheduledFuture<?> task) {
        this.bot = bot;
        this.owner = owner;
        this.task = task;
    }
}
