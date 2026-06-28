package server.bots;

import client.Character;
import client.inventory.Item;
import server.Trade;
import server.life.Monster;
import server.maps.Foothold;
import server.maps.MapItem;
import server.maps.Rope;
import server.agents.commands.AgentQueuedMessage;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;

public class BotEntry {
    public static final class ScrollReactionStreakState {
        public int streak = 0;
        public boolean lastWasSuccess = false;
        public long lastOutcomeAtMs = 0L;
    }

    final Character bot;
    volatile Character owner;
    volatile boolean following = false;
    volatile int followTargetId = 0; // 0 = owner
    volatile boolean airshowActive = false;
    volatile long airshowLastTrailAtMs = 0L;
    final ScheduledFuture<?> task;
    BotMovementProfile movementProfile = BotMovementProfile.base();

    // Physics
    float velY = 0f;
    double hspeed = 0.0;
    double physX = 0.0;
    double physY = 0.0;
    double groundPhysicsCarryMs = 0.0;
    // Peak (min-y = highest point) reached during current airborne period. Used by landOnGround
    // to compute fall distance for fall-damage. Positive infinity when grounded / uninitialised;
    // first airborne-tick lowers it to physY and subsequent ticks keep tracking the peak.
    double fallPeakPhysY = Double.POSITIVE_INFINITY;
    boolean inAir = false;
    int jumpCooldownMs = 0;
    int movementVelX = 0;
    int movementVelY = 0;
    int facingDir = 1;
    boolean crouching = false;
    boolean swimming = false;

    // Swim intent — set by movement layer, consumed by physics engine. Movement
    // expresses "what the bot is trying to do"; physics integrates accordingly.
    // Mirrors how the real client only exposes discrete inputs (steer L/R,
    // jump-burst, hold UP/DOWN) — no continuous velocity overrides.
    int swimMoveDir = 0;                 // -1 left, 0 none, +1 right
    int swimVerticalHold = 0;            // -1 = UP held (slow sink), 0 = none, +1 = DOWN held (fast sink)
    boolean swimJumpRequested = false;   // one-shot upward burst
    long swimNextJumpAtMs = 0L;          // cooldown gate

    // Movement intent — set by movement/fidget layer, consumed by physics engine.
    // Maps to the same left/right key hold used by the real client for both
    // ground walking and air steering. Physics reads this in the active mode:
    //   - Ground: applyGroundMotion() integrates through force/friction model
    //   - Airborne: stepAirborne() applies air steering accel (gated by fixedAirArc)
    // Mutually exclusive by state (inAir vs grounded), so one field suffices.
    int moveDir = 0;                     // -1 left, 0 none, +1 right

    // Rope climbing
    boolean climbing = false;
    Rope climbRope = null;
    Rope blockedRopeGrab = null;

    // Climb intent — set by movement layer, consumed by physics engine.
    int climbVerticalDir = 0;            // -1 up, 0 idle, +1 down

    // Horizontal movement hysteresis
    boolean wasMovingX = false;

    // Committed horizontal step while airborne (set at launch, never changed mid-air)
    int airVelX = 0;
    // Accumulated air-steering correction (gradually adjusted toward target each tick)
    double airSteerVelX = 0.0;
    boolean fixedAirArc = false;

    // Movement intent
    boolean climbUpIntent = false;
    int ropeGrabCooldownMs = 0;

    // Down-jump: true when crouch was shown last tick, jump fires this tick
    boolean downJumpPending = false;
    long downJumpGracePeriodMS = 0;
    boolean ropeEntryPending = false;
    Rope ropeEntryRope = null;
    int ropeEntryY = 0;

    // Grind mode
    volatile boolean grinding = false;
    Monster grindTarget = null;
    long nextGrindTargetSearchAtMs = 0L;
    int attackCooldownMs = 0;
    int moveWindowMs = 0;    // movement-only gap after attack animation; attacks blocked, walking allowed

    public Monster grindTarget() {
        return grindTarget;
    }

    public void setGrindTarget(Monster grindTarget) {
        this.grindTarget = grindTarget;
    }

    public void clearGrindTarget() {
        grindTarget = null;
    }

    public long nextGrindTargetSearchAtMs() {
        return nextGrindTargetSearchAtMs;
    }

    public void setNextGrindTargetSearchAtMs(long nextGrindTargetSearchAtMs) {
        this.nextGrindTargetSearchAtMs = nextGrindTargetSearchAtMs;
    }

    public void clearNextGrindTargetSearchAtMs() {
        nextGrindTargetSearchAtMs = 0L;
    }

    public int attackCooldownMs() {
        return attackCooldownMs;
    }

    public void setAttackCooldownMs(int attackCooldownMs) {
        this.attackCooldownMs = attackCooldownMs;
    }

    public int moveWindowMs() {
        return moveWindowMs;
    }

    public void setMoveWindowMs(int moveWindowMs) {
        this.moveWindowMs = moveWindowMs;
    }

    // Skill cache
    int cachedSkillJob = -1;
    int cachedSkillLevel = -1;
    int cachedSkillSignature = 0;
    final List<Integer> attackSkillIds = new ArrayList<>();
    int attackSkillId = 0;
    int aoeSkillId = 0;
    int aoeSkillMobs = 1;
    int healSkillId = 0;
    List<Integer> buffSkillIds = new ArrayList<>();
    // Summon skills (Phoenix, Puppet, Beholder, ...) classified into their own bucket: they are
    // NOT rebuffable (the bot has no summon-cast path that sends a spawn position, so casting them
    // via the buff loop only burns MP without spawning the creature). Held here for a future
    // place/condition-gated summon caster; the generic rebuff loop ignores this list.
    final List<Integer> summonSkillIds = new ArrayList<>();
    final Map<Integer, Long> nextBuffAt = new HashMap<>();
    final Map<Integer, Long> nextSupportBuffAt = new HashMap<>();
    long nextSupportHealAt = 0L;
    boolean supportHealsEnabled = true;
    boolean skillBuffsEnabled = true;

    public void setSupportHealsEnabled(boolean supportHealsEnabled) {
        this.supportHealsEnabled = supportHealsEnabled;
    }

    public void setSkillBuffsEnabled(boolean skillBuffsEnabled) {
        this.skillBuffsEnabled = skillBuffsEnabled;
    }

    // Ammo
    boolean noAmmo = false;
    boolean ammoWarnSent = false;
    boolean degenAttackDone = false; // force retreat after an accidental close-range hit
    long retreatHoldUntilMs = 0L; // hysteresis: lock the local retreat goal for a short window
    Point retreatHoldPos = null;  // the locked retreat target — reused while hold is active
    int breakoutDirection = 0;    // -1/+1 committed escape side while surrounded, 0 = not breaking out
    long breakoutUntilMs = 0L;    // hard safety timeout for the surround-breakout commitment
    Point aoeRepositionAnchor = null; // committed AoE sweet-spot to walk to before firing, null = not repositioning
    long aoeRepositionDeadlineMs = 0L; // bounded-chase timeout for the AoE reposition commitment
    int wanderDirection = 0;      // -1 left, +1 right, 0 = unset (picked when grind has no target)

    public boolean degenAttackDone() {
        return degenAttackDone;
    }

    public void markDegenAttackDone() {
        degenAttackDone = true;
    }

    public void clearDegenAttackDone() {
        degenAttackDone = false;
    }

    public Point retreatHoldPos() {
        return retreatHoldPos == null ? null : new Point(retreatHoldPos);
    }

    public long retreatHoldUntilMs() {
        return retreatHoldUntilMs;
    }

    public boolean hasRetreatHold() {
        return retreatHoldPos != null;
    }

    public void setRetreatHold(Point position, long untilMs) {
        retreatHoldPos = position == null ? null : new Point(position);
        retreatHoldUntilMs = position == null ? 0L : untilMs;
    }

    public void clearRetreatHold() {
        retreatHoldUntilMs = 0L;
        retreatHoldPos = null;
    }

    public int breakoutDirection() {
        return breakoutDirection;
    }

    public long breakoutUntilMs() {
        return breakoutUntilMs;
    }

    public boolean hasBreakoutCommitment() {
        return breakoutDirection != 0;
    }

    public void setBreakoutCommitment(int direction, long untilMs) {
        breakoutDirection = direction;
        breakoutUntilMs = untilMs;
    }

    public void clearBreakoutCommitment() {
        breakoutDirection = 0;
        breakoutUntilMs = 0L;
    }

    public Point aoeRepositionAnchor() {
        return aoeRepositionAnchor == null ? null : new Point(aoeRepositionAnchor);
    }

    public boolean hasAoeRepositionAnchor() {
        return aoeRepositionAnchor != null;
    }

    public long aoeRepositionDeadlineMs() {
        return aoeRepositionDeadlineMs;
    }

    public void setAoeRepositionAnchor(Point anchor, long deadlineMs) {
        aoeRepositionAnchor = anchor == null ? null : new Point(anchor);
        aoeRepositionDeadlineMs = anchor == null ? 0L : deadlineMs;
    }

    public void clearAoeRepositionAnchor() {
        aoeRepositionAnchor = null;
        aoeRepositionDeadlineMs = 0L;
    }

    // Shop auto-buy (triggered once per map change)
    volatile boolean shopVisitPending = false;
    volatile Point shopNpcPos = null;
    volatile Point shopTargetPos = null;
    int shopApproachDelayMs = 0;
    boolean shopSequenceActive = false;
    long shopVisitStartedAtMs = 0L;
    long shopSequenceStartedAtMs = 0L;
    boolean shopSellTrashPending = false;
    // bumped whenever a new player directive resets scripted state (follow/stop/move/farm/patrol/grind);
    // background batches (Maker crafting / disassembly) capture it and self-interrupt when it changes
    volatile int activityEpoch = 0;
    Point shopStuckCheckPos = null;
    long shopStuckCheckAtMs = 0L;

    // Damage taken
    long deadUntil = 0;
    int mobHitCooldownMs = 0;

    public long deadUntilMs() {
        return deadUntil;
    }

    public void setDeadUntilMs(long deadUntilMs) {
        deadUntil = deadUntilMs;
    }

    public void clearDeadUntilMs() {
        deadUntil = 0L;
    }

    public int mobHitCooldownMs() {
        return mobHitCooldownMs;
    }

    public void setMobHitCooldownMs(int mobHitCooldownMs) {
        this.mobHitCooldownMs = mobHitCooldownMs;
    }

    // Absolute time until which this bot may not take another portal (set on portal use).
    // Portal-only gate: does not block movement, attacks, or any other action.
    long portalUseCooldownUntilMs = 0L;

    public long portalUseCooldownUntilMs() {
        return portalUseCooldownUntilMs;
    }

    public void setPortalUseCooldownUntilMs(long portalUseCooldownUntilMs) {
        this.portalUseCooldownUntilMs = portalUseCooldownUntilMs;
    }
    // Client-side alert-stance emulation: when currentTimeMillis < alertedUntilMs the bot's
    // broadcast stance gets STAND→ALERT substituted so observers see the alert pose.
    // Mirrors CharLook::alerted (TimedBool, 5000ms) in maplestory-wasm. Absolute reset on each
    // trigger (attack/hit/heal/buff), never additive.
    long alertedUntilMs = 0L;
    // Debounce flag for the scheduled stance-reset callback in BotCombatManager.markAlerted.
    // Without this, when the bot stops moving while alerted (e.g. "stay" command), no new
    // movement snapshot ever fires — so the wire stance stays ALERT forever. The callback
    // pushes a fresh STAND broadcast once the timer expires.
    boolean alertResetScheduled = false;

    // Most recent command the owner issued that handleChat actually matched.
    // Used by SituationBuilder to give the LLM context like "owner told you to
    // farm here 3 min ago" so 'what are you doing' answers stay coherent.
    public volatile String lastOwnerCommand = null;
    public volatile long lastOwnerCommandAtMs = 0L;

    public boolean isGrinding() { return grinding; }
    public boolean isFollowing() { return following; }
    public int followTargetId() { return followTargetId; }
    public void setGrinding(boolean grinding) { this.grinding = grinding; }
    public void setFollowing(boolean following) { this.following = following; }
    public void setFollowTargetId(int followTargetId) { this.followTargetId = followTargetId; }
    public Character bot() { return bot; }
    public Character owner() { return owner; }
    public BotBuildManager.ApBuild apBuild() { return apBuild; }
    public void setApBuild(BotBuildManager.ApBuild apBuild) {
        this.apBuild = apBuild;
        this.apPromptSent = false;
    }
    public void clearApBuildPromptState() {
        apBuild = null;
        apPromptSent = false;
    }
    public void markApPromptSent() { this.apPromptSent = true; }
    public void setSpVariant(String spVariant) { this.spVariant = spVariant; }
    public boolean apPromptSent() { return apPromptSent; }
    public String spVariant() { return spVariant; }
    public boolean spVariantPromptSent() { return spVariantPromptSent; }
    public void markSpVariantPromptSent() { this.spVariantPromptSent = true; }
    public java.awt.Point moveTarget() { return moveTarget == null ? null : new java.awt.Point(moveTarget); }
    public boolean isMoveTargetPrecise() { return moveTargetPrecise; }
    public boolean hasMoveTarget() { return moveTarget != null; }
    public void setMoveTarget(java.awt.Point moveTarget, boolean precise) {
        this.moveTarget = moveTarget == null ? null : new java.awt.Point(moveTarget);
        this.moveTargetPrecise = moveTarget != null && precise;
    }
    public void clearMoveTarget() {
        this.moveTarget = null;
        this.moveTargetPrecise = false;
    }
    public boolean moveTargetEquals(java.awt.Point point) {
        return moveTarget != null && moveTarget.equals(point);
    }
    public java.awt.Point farmAnchor() { return farmAnchor == null ? null : new java.awt.Point(farmAnchor); }
    public int farmAnchorMapId() { return farmAnchorMapId; }
    public boolean hasFarmAnchor() { return farmAnchor != null; }
    public void setFarmAnchor(java.awt.Point farmAnchor, int mapId) {
        this.farmAnchor = farmAnchor == null ? null : new java.awt.Point(farmAnchor);
        this.farmAnchorMapId = farmAnchor == null ? -1 : mapId;
    }
    public void clearFarmAnchor() {
        this.farmAnchor = null;
        this.farmAnchorMapId = -1;
    }
    public int patrolRegionId() { return patrolRegionId; }
    public int patrolMapId() { return patrolMapId; }
    public java.awt.Point patrolWanderTarget() {
        return patrolWanderTarget == null ? null : new java.awt.Point(patrolWanderTarget);
    }
    public boolean hasPatrolRegion() { return patrolRegionId >= 0; }
    public void setPatrolRegion(int regionId, int mapId) {
        this.patrolRegionId = regionId;
        this.patrolMapId = regionId < 0 ? -1 : mapId;
        this.patrolWanderTarget = null;
    }
    public void clearPatrol() {
        this.patrolRegionId = -1;
        this.patrolMapId = -1;
        this.patrolWanderTarget = null;
    }
    public void setPatrolWanderTarget(java.awt.Point patrolWanderTarget) {
        this.patrolWanderTarget = patrolWanderTarget == null ? null : new java.awt.Point(patrolWanderTarget);
    }
    public void clearPatrolWanderTarget() {
        this.patrolWanderTarget = null;
    }
    public int wanderDirection() { return wanderDirection; }
    public void setWanderDirection(int wanderDirection) {
        this.wanderDirection = Integer.compare(wanderDirection, 0);
    }
    public void clearWanderDirection() {
        this.wanderDirection = 0;
    }
    public MapItem grindLootTarget() { return grindLootTarget; }
    public boolean hasGrindLootTarget() { return grindLootTarget != null; }
    public void setGrindLootTarget(MapItem grindLootTarget) {
        this.grindLootTarget = grindLootTarget;
    }
    public void clearGrindLootTarget() {
        this.grindLootTarget = null;
    }
    public int ignoredGrindLootObjectId() { return ignoredGrindLootObjectId; }
    public long ignoredGrindLootUntilMs() { return ignoredGrindLootUntilMs; }
    public void suppressGrindLootRetry(int objectId, long untilMs) {
        this.ignoredGrindLootObjectId = objectId;
        this.ignoredGrindLootUntilMs = untilMs;
    }
    public void clearGrindLootRetrySuppression() {
        this.ignoredGrindLootObjectId = 0;
        this.ignoredGrindLootUntilMs = 0L;
    }
    Point lastMobTouchCheckPos = null;
    int lastMobTouchMapId = -1;

    // Loot and potions
    int potCheckTimerMs = 0;
    int mpRecoveryTimerMs = 0;
    int invFullWarnCooldownMs = 0;
    boolean potShareRequestedHp = false; // true once an HP pot-share request has been broadcast this episode
    boolean potShareRequestedMp = false; // reset when pot count recovers above POT_LOW_WARN
    boolean ammoShareRequested = false; // reset when arrow/bolt count recovers above AMMO_LOW_WARN

    public int potCheckTimerMs() {
        return potCheckTimerMs;
    }

    public void setPotCheckTimerMs(int potCheckTimerMs) {
        this.potCheckTimerMs = potCheckTimerMs;
    }

    public int mpRecoveryTimerMs() {
        return mpRecoveryTimerMs;
    }

    public void setMpRecoveryTimerMs(int mpRecoveryTimerMs) {
        this.mpRecoveryTimerMs = mpRecoveryTimerMs;
    }

    public int invFullWarnCooldownMs() {
        return invFullWarnCooldownMs;
    }

    public void setInvFullWarnCooldownMs(int invFullWarnCooldownMs) {
        this.invFullWarnCooldownMs = invFullWarnCooldownMs;
    }

    public boolean potShareRequestedHp() {
        return potShareRequestedHp;
    }

    public void setPotShareRequestedHp(boolean potShareRequestedHp) {
        this.potShareRequestedHp = potShareRequestedHp;
    }

    public boolean potShareRequestedMp() {
        return potShareRequestedMp;
    }

    public void setPotShareRequestedMp(boolean potShareRequestedMp) {
        this.potShareRequestedMp = potShareRequestedMp;
    }

    public boolean ammoShareRequested() {
        return ammoShareRequested;
    }

    public void setAmmoShareRequested(boolean ammoShareRequested) {
        this.ammoShareRequested = ammoShareRequested;
    }

    // Job advancement prompts
    int jobPromptSent = 0;
    int lastKnownLevel = -1;

    public int jobPromptSent() {
        return jobPromptSent;
    }

    public void setJobPromptSent(int jobPromptSent) {
        this.jobPromptSent = jobPromptSent;
    }

    public int lastKnownLevel() {
        return lastKnownLevel;
    }

    public void setLastKnownLevel(int lastKnownLevel) {
        this.lastKnownLevel = lastKnownLevel;
    }

    // AP/SP builds
    BotBuildManager.ApBuild apBuild = null;
    boolean apPromptSent = false;
    String spVariant = null;
    boolean spVariantPromptSent = false;

    // Reply channel — tracks the chat channel the last owner command arrived on.
    // Bot replies are routed to this channel until the next command changes it.
    volatile ReplyChannel replyChannel = ReplyChannel.MAP;

    // Pending two-step action
    String pendingAction = null;
    public String pendingAction() { return pendingAction; }
    public void setPendingAction(String pendingAction) { this.pendingAction = pendingAction; }
    public void clearPendingAction() { this.pendingAction = null; }
    String pendingDropCategory = null;
    public String pendingDropCategory() { return pendingDropCategory; }
    public void setPendingDropCategory(String pendingDropCategory) { this.pendingDropCategory = pendingDropCategory; }
    public void clearPendingDropCategory() { this.pendingDropCategory = null; }
    Item pendingLootOfferItem = null;
    public Item pendingLootOfferItem() { return pendingLootOfferItem; }
    int pendingLootOfferRecipientId = 0;
    long pendingLootOfferExpiresAt = 0L;
    int lootInhibitMs = 0;

    public int lootInhibitMs() {
        return lootInhibitMs;
    }

    public void setLootInhibitMs(int lootInhibitMs) {
        this.lootInhibitMs = lootInhibitMs;
    }

    // Bot-initiated trade retry: when a pot-share / ammo-share / loot-offer is blocked
    // because the sender or recipient is already in a trade, the attempt is stored here
    // and re-fired once the sender's trade clears and the delay expires.
    Runnable pendingBotTradeRetry = null;
    int pendingBotTradeRetryMs = 0;

    public Runnable pendingBotTradeRetry() {
        return pendingBotTradeRetry;
    }

    public void setPendingBotTradeRetry(Runnable pendingBotTradeRetry) {
        this.pendingBotTradeRetry = pendingBotTradeRetry;
    }

    public int pendingBotTradeRetryMs() {
        return pendingBotTradeRetryMs;
    }

    public void setPendingBotTradeRetryMs(int pendingBotTradeRetryMs) {
        this.pendingBotTradeRetryMs = pendingBotTradeRetryMs;
    }

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
    boolean pendingTradeInviteAnnounced = false;
    String  pendingTradeCategoryMsg = null;
    int     pendingPotShareBudget = 0; // max total qty to donate; 0 = no cap (normal trades)
    Map<Item, Short> pendingTradeRestoreSlots = new IdentityHashMap<>();

    public String pendingTradeCategory() {
        return pendingTradeCategory;
    }

    public void setPendingTradeCategory(String pendingTradeCategory) {
        this.pendingTradeCategory = pendingTradeCategory;
    }

    public List<Item> pendingTradeItems() {
        return pendingTradeItems;
    }

    public void setPendingTradeItems(List<Item> pendingTradeItems) {
        this.pendingTradeItems = pendingTradeItems;
    }

    public int pendingPotShareBudget() {
        return pendingPotShareBudget;
    }

    public void setPendingPotShareBudget(int pendingPotShareBudget) {
        this.pendingPotShareBudget = pendingPotShareBudget;
    }

    public String pendingTradeCategoryMsg() {
        return pendingTradeCategoryMsg;
    }

    public void setPendingTradeCategoryMsg(String pendingTradeCategoryMsg) {
        this.pendingTradeCategoryMsg = pendingTradeCategoryMsg;
    }

    public int pendingTradeRecipientId() {
        return pendingTradeRecipientId;
    }

    public void setPendingTradeRecipientId(int pendingTradeRecipientId) {
        this.pendingTradeRecipientId = pendingTradeRecipientId;
    }

    public boolean pendingTradeInviteAnnounced() {
        return pendingTradeInviteAnnounced;
    }

    public void setPendingTradeInviteAnnounced(boolean pendingTradeInviteAnnounced) {
        this.pendingTradeInviteAnnounced = pendingTradeInviteAnnounced;
    }

    public int pendingTradeTimerMs() {
        return pendingTradeTimerMs;
    }

    public void setPendingTradeTimerMs(int pendingTradeTimerMs) {
        this.pendingTradeTimerMs = pendingTradeTimerMs;
    }

    public boolean pendingTradeSingleBatch() {
        return pendingTradeSingleBatch;
    }

    public void setPendingTradeSingleBatch(boolean pendingTradeSingleBatch) {
        this.pendingTradeSingleBatch = pendingTradeSingleBatch;
    }

    public int pendingTradeMeso() {
        return pendingTradeMeso;
    }

    public void setPendingTradeMeso(int pendingTradeMeso) {
        this.pendingTradeMeso = pendingTradeMeso;
    }

    public boolean pendingTradeMesoAdded() {
        return pendingTradeMesoAdded;
    }

    public void setPendingTradeMesoAdded(boolean pendingTradeMesoAdded) {
        this.pendingTradeMesoAdded = pendingTradeMesoAdded;
    }

    public boolean pendingTradeAllAdded() {
        return pendingTradeAllAdded;
    }

    public void setPendingTradeAllAdded(boolean pendingTradeAllAdded) {
        this.pendingTradeAllAdded = pendingTradeAllAdded;
    }

    public boolean pendingTradeBotDone() {
        return pendingTradeBotDone;
    }

    public void setPendingTradeBotDone(boolean pendingTradeBotDone) {
        this.pendingTradeBotDone = pendingTradeBotDone;
    }

    public int pendingTradeIdx() {
        return pendingTradeIdx;
    }

    public void setPendingTradeIdx(int pendingTradeIdx) {
        this.pendingTradeIdx = pendingTradeIdx;
    }

    public Map<Item, Short> pendingTradeRestoreSlots() {
        return pendingTradeRestoreSlots;
    }

    // Message queue
    final ArrayDeque<AgentQueuedMessage> msgQueue = new ArrayDeque<>();
    boolean msgSending = false;

    public Deque<AgentQueuedMessage> messageQueue() {
        return msgQueue;
    }

    public boolean isMessageSending() {
        return msgSending;
    }

    public void setMessageSending(boolean msgSending) {
        this.msgSending = msgSending;
    }

    // Generic scripted task queue. Per-map scripts enqueue small primitives
    // (move, follow, grind, drop) and the shared manager executes them.
    final ArrayDeque<BotTask> scriptTasks = new ArrayDeque<>();
    BotTask activeScriptTask = null;

    public int activityEpoch() {
        return activityEpoch;
    }

    public int bumpActivityEpoch() {
        return ++activityEpoch;
    }

    public void addScriptTask(BotTask task) {
        scriptTasks.add(task);
    }

    public BotTask activeScriptTask() {
        return activeScriptTask;
    }

    public void setActiveScriptTask(BotTask activeScriptTask) {
        this.activeScriptTask = activeScriptTask;
    }

    public BotTask pollScriptTask() {
        return scriptTasks.poll();
    }

    public boolean hasScriptTasks() {
        return activeScriptTask != null || !scriptTasks.isEmpty();
    }

    public void clearScriptTasks() {
        scriptTasks.clear();
        activeScriptTask = null;
    }

    // AFK detection
    Point ownerAfkPos = null;
    long ownerAfkSinceMs = 0;
    boolean ownerWasAfk = false;

    public Point ownerAfkPosition() {
        return ownerAfkPos;
    }

    public void setOwnerAfkPosition(Point ownerAfkPos) {
        this.ownerAfkPos = ownerAfkPos;
    }

    public long ownerAfkSinceMs() {
        return ownerAfkSinceMs;
    }

    public void setOwnerAfkSinceMs(long ownerAfkSinceMs) {
        this.ownerAfkSinceMs = ownerAfkSinceMs;
    }

    public boolean ownerWasAfk() {
        return ownerWasAfk;
    }

    public void setOwnerWasAfk(boolean ownerWasAfk) {
        this.ownerWasAfk = ownerWasAfk;
    }

    // Owner-offline-or-dead detection: after a sustained period (5 min) the bot
    // scrolls/warps to the nearest town and idles, instead of grinding pots dry
    // or death-looping with no anchor.
    long ownerOfflineOrDeadSinceMs = 0;
    boolean ownerReturnedToTown = false;
    boolean ownerAwaySafeMode = false;

    public long ownerOfflineOrDeadSinceMs() {
        return ownerOfflineOrDeadSinceMs;
    }

    public void setOwnerOfflineOrDeadSinceMs(long ownerOfflineOrDeadSinceMs) {
        this.ownerOfflineOrDeadSinceMs = ownerOfflineOrDeadSinceMs;
    }

    public boolean ownerReturnedToTown() {
        return ownerReturnedToTown;
    }

    public void setOwnerReturnedToTown(boolean ownerReturnedToTown) {
        this.ownerReturnedToTown = ownerReturnedToTown;
    }

    public boolean ownerAwaySafeMode() {
        return ownerAwaySafeMode;
    }

    public void setOwnerAwaySafeMode(boolean ownerAwaySafeMode) {
        this.ownerAwaySafeMode = ownerAwaySafeMode;
    }

    // Foothold index, rebuilt on map change
    int lastMapId = -1;
    Map<Integer, Foothold> fhIndex = new HashMap<>();

    public int lastMapId() {
        return lastMapId;
    }

    public Map<Integer, Foothold> footholdIndex() {
        return Collections.unmodifiableMap(fhIndex);
    }

    public void setMapTracking(int mapId, Map<Integer, Foothold> footholdIndex) {
        lastMapId = mapId;
        fhIndex = footholdIndex == null ? new HashMap<>() : new HashMap<>(footholdIndex);
    }

    // Human-like spacing and stagger — assigned at registration based on bot index
    int followOffsetX = 0;

    public int followOffsetX() {
        return followOffsetX;
    }

    public void setFollowOffsetX(int followOffsetX) {
        this.followOffsetX = followOffsetX;
    }

    int skipDelayMs = ThreadLocalRandom.current().nextInt(0, 501);
    int aiTickAccumulatorMs = 0;

    public int skipDelayMs() {
        return skipDelayMs;
    }

    public void setSkipDelayMs(int skipDelayMs) {
        this.skipDelayMs = skipDelayMs;
    }

    public int aiTickAccumulatorMs() {
        return aiTickAccumulatorMs;
    }

    public void setAiTickAccumulatorMs(int aiTickAccumulatorMs) {
        this.aiTickAccumulatorMs = aiTickAccumulatorMs;
    }

    // "Move here" target — bot navigates to this fixed point, then idles until cleared
    Point moveTarget = null;
    boolean moveTargetPrecise = false; // true when triggered by "move here" — uses tight stop dist
    // "Farm here" anchor — bot returns to this fixed point and only takes local attacks.
    Point farmAnchor = null;
    int farmAnchorMapId = -1;
    // Grind loot — nearest convenient drop, searched each AI tick, cleared when picked up.
    MapItem grindLootTarget = null;
    int ignoredGrindLootObjectId = 0;
    long ignoredGrindLootUntilMs = 0L;
    // "Patrol" region — bot wanders within this nav region and attacks opportunistically.
    int patrolRegionId = -1;    // BotNavigationGraph.Region id; -1 = inactive
    int patrolMapId = -1;
    Point patrolWanderTarget = null;

    // Buff consumables (toggleable; cheap = weakest buff of each type, max = strongest)
    boolean buffConsumablesEnabled = false;
    boolean buffCheapMode          = true;
    boolean proactiveUpgradeOffers = true;
    long    lastBuffScanMs         = 0;
    long    lastBuffActionAtMs     = 0L;
    String  lastBuffActionSummary  = "no buff scans yet";

    public long lastBuffScanMs() {
        return lastBuffScanMs;
    }

    public void setLastBuffScanMs(long lastBuffScanMs) {
        this.lastBuffScanMs = lastBuffScanMs;
    }

    public long lastBuffActionAtMs() {
        return lastBuffActionAtMs;
    }

    public String lastBuffActionSummary() {
        return lastBuffActionSummary;
    }

    public void setLastBuffAction(long atMs, String summary) {
        this.lastBuffActionAtMs = atMs;
        this.lastBuffActionSummary = summary;
    }

    public boolean buffConsumablesEnabled() {
        return buffConsumablesEnabled;
    }

    public void setBuffConsumablesEnabled(boolean buffConsumablesEnabled) {
        this.buffConsumablesEnabled = buffConsumablesEnabled;
    }

    public boolean buffCheapMode() {
        return buffCheapMode;
    }

    public void setBuffCheapMode(boolean buffCheapMode) {
        this.buffCheapMode = buffCheapMode;
    }

    public void resetLastBuffScan() {
        lastBuffScanMs = 0;
    }

    public void setProactiveUpgradeOffers(boolean proactiveUpgradeOffers) {
        this.proactiveUpgradeOffers = proactiveUpgradeOffers;
    }

    // Skill buff tracking (always enabled; tracks last decision for debug)
    long   lastSkillBuffActionAtMs    = 0L;
    String lastSkillBuffActionSummary = "no skill buff checks yet";

    // Party-quest state (one slot per PQ type; null = not in that PQ)
    public server.bots.pq.BotKpqState kpq = new server.bots.pq.BotKpqState();
    public BotScriptRuntime script = new BotScriptRuntime();

    // Equips received from the owner during the current trade session.
    // Cleared when that trade session finishes or is cancelled.
    Set<Item> ownerGivenItems = Collections.newSetFromMap(new IdentityHashMap<>());

    // Last reason an edge execution was blocked (for debug logs)
    String lastEdgeBlockReason = null;

    // Cached movement state shared across ticks
    Point navTargetPos = null;
    BotNavigationGraph.Edge navEdge = null;
    BotNavigationGraph.Edge navJumpLaunchEdge = null;
    int navJumpLaunchX = Integer.MIN_VALUE;
    int navTargetRegionId = -1;
    boolean navPreciseTarget = false;
    boolean graphWarmupFallback = false;
    int observedOwnerStepX = 0;
    int observedOwnerStepY = 0;

    public int observedOwnerStepX() {
        return observedOwnerStepX;
    }

    public int observedOwnerStepY() {
        return observedOwnerStepY;
    }

    public void setObservedOwnerStep(int stepX, int stepY) {
        this.observedOwnerStepX = stepX;
        this.observedOwnerStepY = stepY;
    }
    BotFidgetMode fidgetMode = BotFidgetMode.NONE;
    BotFidgetTrigger fidgetTrigger = BotFidgetTrigger.NONE;
    long fidgetUntilMs = 0L;
    long nextFidgetActionAtMs = 0L;
    long nextFidgetAtMs = 0L;
    long nextIdleFidgetRollAtMs = 0L;
    int fidgetAirSteerDir = 0;
    int fidgetJumpDir = 0;
    int fidgetMoveDir = 0;
    boolean fidgetSpamAirSteer = false;
    int fidgetActionBaseDelayMs = 0;
    long nextFidgetJumpAtMs = 0L;
    Point fidgetOriginPos = null;
    long nextFidgetVisualAtMs = 0L;
    long nextGearSuggestionAt = 0L;
    boolean spawnUpgradeCheckDone = false;

    public long nextGearSuggestionAt() {
        return nextGearSuggestionAt;
    }

    public void setNextGearSuggestionAt(long nextGearSuggestionAt) {
        this.nextGearSuggestionAt = nextGearSuggestionAt;
    }

    public boolean spawnUpgradeCheckDone() {
        return spawnUpgradeCheckDone;
    }

    public void setSpawnUpgradeCheckDone(boolean spawnUpgradeCheckDone) {
        this.spawnUpgradeCheckDone = spawnUpgradeCheckDone;
    }
    final Set<Integer> requestedUpgradeItemIds = ConcurrentHashMap.newKeySet();
    boolean pendingLootOfferBotRequesting = false; // true = bot asked for owner's item
    double recentScrollReactionLoad = 0.0;
    long lastScrollReactionObservedAtMs = 0L;
    long nextScrollReactionAtMs = 0L;
    final Map<Integer, ScrollReactionStreakState> scrollReactionStreaksByScroller = new HashMap<>();
    long nextScrollReactionStreakPruneAtMs = 0L;

    // Path logging (debug)
    BotPathLogger pathLogger = null;
    String lastNavDecision = "-";
    long pendingGearPromptAt = 0L;

    public BotPathLogger pathLogger() {
        return pathLogger;
    }

    public void setPathLogger(BotPathLogger pathLogger) {
        this.pathLogger = pathLogger;
    }

    public void clearPathLogger() {
        this.pathLogger = null;
    }

    public String lastNavDecision() {
        return lastNavDecision;
    }

    public void setLastNavDecision(String lastNavDecision) {
        this.lastNavDecision = lastNavDecision;
    }

    public String lastEdgeBlockReason() {
        return lastEdgeBlockReason;
    }

    public void setLastEdgeBlockReason(String lastEdgeBlockReason) {
        this.lastEdgeBlockReason = lastEdgeBlockReason;
    }

    public boolean graphWarmupFallback() {
        return graphWarmupFallback;
    }

    public void setGraphWarmupFallback(boolean graphWarmupFallback) {
        this.graphWarmupFallback = graphWarmupFallback;
    }

    public Point navTargetPos() {
        return navTargetPos == null ? null : new Point(navTargetPos);
    }

    public void setNavTargetPos(Point navTargetPos) {
        this.navTargetPos = navTargetPos == null ? null : new Point(navTargetPos);
    }

    public int navTargetRegionId() {
        return navTargetRegionId;
    }

    public void setNavTargetRegionId(int navTargetRegionId) {
        this.navTargetRegionId = navTargetRegionId;
    }

    public boolean navPreciseTarget() {
        return navPreciseTarget;
    }

    public void setNavPreciseTarget(boolean navPreciseTarget) {
        this.navPreciseTarget = navPreciseTarget;
    }

    public boolean matchesNavJumpLaunchEdge(Object edge) {
        return edge instanceof BotNavigationGraph.Edge navEdge && sameNavEdge(navJumpLaunchEdge, navEdge);
    }

    public boolean hasNavJumpLaunchEdge() {
        return navJumpLaunchEdge != null;
    }

    public int navJumpLaunchX() {
        return navJumpLaunchX;
    }

    public void setNavJumpLaunch(Object navJumpLaunchEdge, int navJumpLaunchX) {
        this.navJumpLaunchEdge = navJumpLaunchEdge instanceof BotNavigationGraph.Edge navEdge ? navEdge : null;
        this.navJumpLaunchX = navJumpLaunchX;
    }

    private static boolean sameNavEdge(BotNavigationGraph.Edge a, BotNavigationGraph.Edge b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.type == b.type
                && a.fromRegionId == b.fromRegionId
                && a.toRegionId == b.toRegionId
                && a.startPoint.equals(b.startPoint)
                && a.endPoint.equals(b.endPoint)
                && a.portalId == b.portalId
                && a.launchMinX == b.launchMinX
                && a.launchMaxX == b.launchMaxX;
    }

    public long pendingGearPromptAt() {
        return pendingGearPromptAt;
    }

    public double recentScrollReactionLoad() {
        return recentScrollReactionLoad;
    }

    public void setRecentScrollReactionLoad(double recentScrollReactionLoad) {
        this.recentScrollReactionLoad = recentScrollReactionLoad;
    }

    public long lastScrollReactionObservedAtMs() {
        return lastScrollReactionObservedAtMs;
    }

    public void setLastScrollReactionObservedAtMs(long lastScrollReactionObservedAtMs) {
        this.lastScrollReactionObservedAtMs = lastScrollReactionObservedAtMs;
    }

    public long nextScrollReactionAtMs() {
        return nextScrollReactionAtMs;
    }

    public void setNextScrollReactionAtMs(long nextScrollReactionAtMs) {
        this.nextScrollReactionAtMs = nextScrollReactionAtMs;
    }

    public Map<Integer, ScrollReactionStreakState> scrollReactionStreaksByScroller() {
        return scrollReactionStreaksByScroller;
    }

    public long nextScrollReactionStreakPruneAtMs() {
        return nextScrollReactionStreakPruneAtMs;
    }

    public void setNextScrollReactionStreakPruneAtMs(long nextScrollReactionStreakPruneAtMs) {
        this.nextScrollReactionStreakPruneAtMs = nextScrollReactionStreakPruneAtMs;
    }

    public void setPendingGearPromptAt(long pendingGearPromptAt) {
        this.pendingGearPromptAt = pendingGearPromptAt;
    }

    // Last known owner position (set each tick in BotManager, read by pathLogger)
    Point lastOwnerPos = null;
    boolean lastTickWasAi = false;
    long lastTickAtMs = 0L;
    long lastHeartbeatAtMs = 0L;
    long nextFollowIdleMovementCheckAtMs = 0L;

    public Point lastOwnerPosition() {
        return lastOwnerPos == null ? null : new Point(lastOwnerPos);
    }

    public void setLastOwnerPosition(Point lastOwnerPos) {
        this.lastOwnerPos = lastOwnerPos == null ? null : new Point(lastOwnerPos);
    }

    public boolean lastTickWasAi() {
        return lastTickWasAi;
    }

    public long lastTickAtMs() {
        return lastTickAtMs;
    }

    public void recordTick(boolean aiTick, long tickAtMs) {
        this.lastTickWasAi = aiTick;
        this.lastTickAtMs = tickAtMs;
    }

    public long lastHeartbeatAtMs() {
        return lastHeartbeatAtMs;
    }

    public void setLastHeartbeatAtMs(long lastHeartbeatAtMs) {
        this.lastHeartbeatAtMs = lastHeartbeatAtMs;
    }

    public long nextFollowIdleMovementCheckAtMs() {
        return nextFollowIdleMovementCheckAtMs;
    }

    public void setNextFollowIdleMovementCheckAtMs(long nextFollowIdleMovementCheckAtMs) {
        this.nextFollowIdleMovementCheckAtMs = nextFollowIdleMovementCheckAtMs;
    }

    int tickFailureCount = 0;
    long tickFailureWindowStartedAtMs = 0L;

    public int tickFailureCount() {
        return tickFailureCount;
    }

    public long tickFailureWindowStartedAtMs() {
        return tickFailureWindowStartedAtMs;
    }

    public void resetTickFailureWindow(long startedAtMs) {
        tickFailureWindowStartedAtMs = startedAtMs;
        tickFailureCount = 0;
    }

    public int incrementTickFailureCount() {
        tickFailureCount++;
        return tickFailureCount;
    }

    public void clearTickFailures() {
        tickFailureCount = 0;
        tickFailureWindowStartedAtMs = 0L;
    }

    // Stuck detection & unstuck
    int stuckMs = 0;
    int unstuckCooldownMs = 0;
    int stuckCheckX = Integer.MIN_VALUE;
    int stuckCheckY = Integer.MIN_VALUE;

    public int stuckMs() {
        return stuckMs;
    }

    public void setStuckMs(int stuckMs) {
        this.stuckMs = stuckMs;
    }

    public void addStuckMs(int deltaMs) {
        this.stuckMs += deltaMs;
    }

    public int unstuckCooldownMs() {
        return unstuckCooldownMs;
    }

    public void setUnstuckCooldownMs(int unstuckCooldownMs) {
        this.unstuckCooldownMs = unstuckCooldownMs;
    }

    public int stuckCheckX() {
        return stuckCheckX;
    }

    public int stuckCheckY() {
        return stuckCheckY;
    }

    public boolean hasStuckCheckPosition() {
        return stuckCheckX != Integer.MIN_VALUE;
    }

    public void setStuckCheckPosition(Point position) {
        this.stuckCheckX = position.x;
        this.stuckCheckY = position.y;
    }

    public void clearStuckCheckPosition() {
        this.stuckCheckX = Integer.MIN_VALUE;
    }

    // Manual trade: countdown before bot accepts an incoming trade invite (both owner and peer-bot)
    int manualTradeAcceptDelayMs = 0;
    Trade manualTradeRef = null;
    int manualTradeTimeoutMs = 0;

    public int manualTradeAcceptDelayMs() {
        return manualTradeAcceptDelayMs;
    }

    public void setManualTradeAcceptDelayMs(int manualTradeAcceptDelayMs) {
        this.manualTradeAcceptDelayMs = manualTradeAcceptDelayMs;
    }

    public Trade manualTradeRef() {
        return manualTradeRef;
    }

    public void setManualTradeRef(Trade manualTradeRef) {
        this.manualTradeRef = manualTradeRef;
    }

    public int manualTradeTimeoutMs() {
        return manualTradeTimeoutMs;
    }

    public void setManualTradeTimeoutMs(int manualTradeTimeoutMs) {
        this.manualTradeTimeoutMs = manualTradeTimeoutMs;
    }

    // Movement packet cache so repeated no-op packets are suppressed
    boolean movementBroadcastValid = false;
    int lastBroadcastX = 0;
    int lastBroadcastY = 0;
    int lastBroadcastVelX = 0;
    int lastBroadcastVelY = 0;
    int lastBroadcastStance = 0;
    int lastBroadcastFh = 0;
    int lastGroundFhId = 0;

    public boolean movementBroadcastValid() {
        return movementBroadcastValid;
    }

    public void setMovementBroadcastValid(boolean movementBroadcastValid) {
        this.movementBroadcastValid = movementBroadcastValid;
    }

    public int lastBroadcastX() {
        return lastBroadcastX;
    }

    public void setLastBroadcastX(int lastBroadcastX) {
        this.lastBroadcastX = lastBroadcastX;
    }

    public int lastBroadcastY() {
        return lastBroadcastY;
    }

    public void setLastBroadcastY(int lastBroadcastY) {
        this.lastBroadcastY = lastBroadcastY;
    }

    public int lastBroadcastVelX() {
        return lastBroadcastVelX;
    }

    public void setLastBroadcastVelX(int lastBroadcastVelX) {
        this.lastBroadcastVelX = lastBroadcastVelX;
    }

    public int lastBroadcastVelY() {
        return lastBroadcastVelY;
    }

    public void setLastBroadcastVelY(int lastBroadcastVelY) {
        this.lastBroadcastVelY = lastBroadcastVelY;
    }

    public int lastBroadcastStance() {
        return lastBroadcastStance;
    }

    public void setLastBroadcastStance(int lastBroadcastStance) {
        this.lastBroadcastStance = lastBroadcastStance;
    }

    public int lastBroadcastFh() {
        return lastBroadcastFh;
    }

    public void setLastBroadcastFh(int lastBroadcastFh) {
        this.lastBroadcastFh = lastBroadcastFh;
    }

    BotEntry(Character bot, Character owner, ScheduledFuture<?> task) {
        this.bot = bot;
        this.owner = owner;
        this.task = task;
    }

    // Accessors for code outside the server.bots package (e.g. server.bots.llm).
    // Mutations stay package-private to preserve existing invariants.
    public Character getBot() { return bot; }
    public Character getOwner() { return owner; }
    public ReplyChannel getReplyChannel() { return replyChannel; }
    public void setReplyChannel(ReplyChannel replyChannel) { this.replyChannel = replyChannel; }
}
