package server.bots;

import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationDebugState;
import server.agents.capabilities.navigation.AgentNavigationEdgeState;
import server.agents.capabilities.navigation.AgentNavigationTargetState;
import server.agents.capabilities.navigation.AgentPortalCooldownState;

import server.agents.capabilities.build.AgentBuildService;
import server.agents.capabilities.build.AgentBuildState;
import server.agents.capabilities.combat.AgentBuffState;
import server.agents.capabilities.combat.AgentCombatCooldownState;
import server.agents.capabilities.combat.AgentMobTouchState;

import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.movement.AgentGroundTravelState;

import client.Character;
import client.inventory.Item;
import server.Trade;
import server.life.Monster;
import server.maps.Foothold;
import server.maps.MapItem;
import server.maps.Rope;
import server.agents.commands.AgentMessageQueueState;
import server.agents.commands.AgentQueuedMessage;
import server.agents.commands.AgentReplyChannel;
import server.agents.capabilities.dialogue.AgentPendingActionState;
import server.agents.capabilities.inventory.AgentInventoryCooldownState;
import server.agents.capabilities.social.AgentScrollReactionState;
import server.agents.capabilities.shop.AgentShopState;
import server.agents.capabilities.supplies.AgentAmmoSupplyState;
import server.agents.capabilities.supplies.AgentPotionSupplyState;
import server.agents.capabilities.social.airshow.AgentAirshowState;
import server.agents.capabilities.movement.fidget.AgentFidgetMode;
import server.agents.capabilities.movement.fidget.AgentFidgetTrigger;
import server.agents.capabilities.trade.AgentPendingLootOfferState;
import server.agents.capabilities.trade.AgentManualTradeState;
import server.agents.capabilities.trade.AgentTradeRetryState;
import server.agents.capabilities.trade.AgentUpgradeOfferState;
import server.agents.monitoring.AgentPathLogger;
import server.agents.plans.AgentTask;
import server.agents.plans.AgentScriptTaskQueueState;
import server.agents.plans.AgentScriptRuntimeState;
import server.agents.runtime.AgentFormationOffsetState;
import server.agents.runtime.AgentDeathState;
import server.agents.runtime.AgentLeaderActivityState;
import server.agents.runtime.AgentMapTrackingState;
import server.agents.runtime.AgentMovementBroadcastState;
import server.agents.runtime.AgentMovementStuckState;
import server.agents.runtime.AgentOwnerMotionState;
import server.agents.runtime.AgentTickFailureState;
import server.agents.runtime.AgentTickState;

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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;

public class BotEntry {
    final Character bot;
    volatile Character owner;
    volatile boolean following = false;
    volatile int followTargetId = 0; // 0 = owner
    private final AgentAirshowState airshowState = new AgentAirshowState();
    final ScheduledFuture<?> task;
    AgentMovementProfile movementProfile = AgentMovementProfile.base();

    public boolean hasScheduledTask() {
        return task != null;
    }

    public void cancelScheduledTask() {
        if (task != null) {
            task.cancel(false);
        }
    }

    public boolean airshowActive() {
        return airshowState.active();
    }

    public void setAirshowActive(boolean airshowActive) {
        airshowState.setActive(airshowActive);
    }

    public long airshowLastTrailAtMs() {
        return airshowState.lastTrailAtMs();
    }

    public void setAirshowLastTrailAtMs(long airshowLastTrailAtMs) {
        airshowState.setLastTrailAtMs(airshowLastTrailAtMs);
    }

    public AgentAirshowState airshowState() {
        return airshowState;
    }

    public AgentMovementProfile movementProfile() {
        return movementProfile != null ? movementProfile : AgentMovementProfile.base();
    }

    public void setMovementProfile(AgentMovementProfile movementProfile) {
        this.movementProfile = movementProfile != null ? movementProfile : AgentMovementProfile.base();
    }

    // Physics
    float velY = 0f;

    public float verticalVelocity() {
        return velY;
    }

    public void setVerticalVelocity(float verticalVelocity) {
        velY = verticalVelocity;
    }

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

    public boolean inAir() {
        return inAir;
    }

    public void setInAir(boolean inAir) {
        this.inAir = inAir;
    }

    public double fallPeakPhysicsY() {
        return fallPeakPhysY;
    }

    public void setFallPeakPhysicsY(double fallPeakPhysicsY) {
        fallPeakPhysY = fallPeakPhysicsY;
    }

    public void resetFallPeakPhysicsY() {
        fallPeakPhysY = Double.POSITIVE_INFINITY;
    }

    public AgentGroundTravelState groundTravelState() {
        return new AgentGroundTravelState(physX, hspeed, groundPhysicsCarryMs);
    }

    public double horizontalSpeed() {
        return hspeed;
    }

    public void setHorizontalSpeed(double horizontalSpeed) {
        hspeed = horizontalSpeed;
    }

    public double physicsX() {
        return physX;
    }

    public double physicsY() {
        return physY;
    }

    public void setPhysicsX(double physicsX) {
        physX = physicsX;
    }

    public void setPhysicsY(double physicsY) {
        physY = physicsY;
    }

    public void setPhysicsPosition(double physicsX, double physicsY) {
        physX = physicsX;
        physY = physicsY;
    }

    public void addPhysicsPosition(double deltaX, double deltaY) {
        physX += deltaX;
        physY += deltaY;
    }

    public void setPhysicsPosition(Point position) {
        if (position != null) {
            setPhysicsPosition(position.x, position.y);
        }
    }

    public double groundPhysicsCarryMs() {
        return groundPhysicsCarryMs;
    }

    public void setGroundPhysicsCarryMs(double groundPhysicsCarryMs) {
        this.groundPhysicsCarryMs = groundPhysicsCarryMs;
    }

    public int jumpCooldownMs() {
        return jumpCooldownMs;
    }

    public void setJumpCooldownMs(int jumpCooldownMs) {
        this.jumpCooldownMs = jumpCooldownMs;
    }

    public int facingDirection() {
        return facingDir;
    }

    public void setFacingDirection(int facingDirection) {
        facingDir = facingDirection >= 0 ? 1 : -1;
    }

    public void setScriptedMovementFrame(Point position,
                                         int velocityX,
                                         int velocityY,
                                         int facingDirection,
                                         boolean inAir,
                                         boolean climbing) {
        physX = position.x;
        physY = position.y;
        movementVelX = velocityX;
        movementVelY = velocityY;
        setFacingDirection(facingDirection);
        this.inAir = inAir;
        this.climbing = climbing;
    }

    public int movementVelX() {
        return movementVelX;
    }

    public int movementVelY() {
        return movementVelY;
    }

    public boolean hasMovementVelocity() {
        return movementVelX != 0 || movementVelY != 0;
    }

    public void setMovementVelocity(int velocityX, int velocityY) {
        movementVelX = velocityX;
        movementVelY = velocityY;
        if (velocityX != 0) {
            setFacingDirection(velocityX);
        }
    }

    // Swim intent — set by movement layer, consumed by physics engine. Movement
    // expresses "what the bot is trying to do"; physics integrates accordingly.
    // Mirrors how the real client only exposes discrete inputs (steer L/R,
    // jump-burst, hold UP/DOWN) — no continuous velocity overrides.
    int swimMoveDir = 0;                 // -1 left, 0 none, +1 right
    int swimVerticalHold = 0;            // -1 = UP held (slow sink), 0 = none, +1 = DOWN held (fast sink)
    boolean swimJumpRequested = false;   // one-shot upward burst
    long swimNextJumpAtMs = 0L;          // cooldown gate

    public boolean swimming() {
        return swimming;
    }

    public void setSwimming(boolean swimming) {
        this.swimming = swimming;
    }

    public int swimMoveDirection() {
        return swimMoveDir;
    }

    public void setSwimMoveDirection(int direction) {
        swimMoveDir = Integer.compare(direction, 0);
    }

    public int swimVerticalHold() {
        return swimVerticalHold;
    }

    public void setSwimVerticalHold(int verticalHold) {
        swimVerticalHold = Integer.compare(verticalHold, 0);
    }

    public boolean swimJumpRequested() {
        return swimJumpRequested;
    }

    public void setSwimJumpRequested(boolean swimJumpRequested) {
        this.swimJumpRequested = swimJumpRequested;
    }

    public long swimNextJumpAtMs() {
        return swimNextJumpAtMs;
    }

    public void setSwimNextJumpAtMs(long swimNextJumpAtMs) {
        this.swimNextJumpAtMs = swimNextJumpAtMs;
    }

    // Movement intent — set by movement/fidget layer, consumed by physics engine.
    // Maps to the same left/right key hold used by the real client for both
    // ground walking and air steering. Physics reads this in the active mode:
    //   - Ground: applyGroundMotion() integrates through force/friction model
    //   - Airborne: stepAirborne() applies air steering accel (gated by fixedAirArc)
    // Mutually exclusive by state (inAir vs grounded), so one field suffices.
    int moveDir = 0;                     // -1 left, 0 none, +1 right

    public int moveDirection() {
        return moveDir;
    }

    public void setMoveDirection(int moveDirection) {
        moveDir = Integer.compare(moveDirection, 0);
    }

    public void clearMoveDirection() {
        moveDir = 0;
    }

    // Rope climbing
    boolean climbing = false;
    Rope climbRope = null;
    Rope blockedRopeGrab = null;

    public boolean climbing() {
        return climbing;
    }

    // Climb intent — set by movement layer, consumed by physics engine.
    public Rope climbRope() {
        return climbRope;
    }

    public void setClimbingOnRope(Rope rope) {
        climbing = rope != null;
        climbRope = rope;
    }

    int climbVerticalDir = 0;            // -1 up, 0 idle, +1 down

    public int climbVerticalDirection() {
        return climbVerticalDir;
    }

    public void setClimbVerticalDirection(int direction) {
        climbVerticalDir = Integer.compare(direction, 0);
    }

    // Horizontal movement hysteresis
    boolean wasMovingX = false;

    public boolean wasMovingX() {
        return wasMovingX;
    }

    public void setWasMovingX(boolean wasMovingX) {
        this.wasMovingX = wasMovingX;
    }

    // Committed horizontal step while airborne (set at launch, never changed mid-air)
    int airVelX = 0;

    public int airVelocityX() {
        return airVelX;
    }

    public void setAirVelocityX(int airVelocityX) {
        airVelX = airVelocityX;
    }

    // Accumulated air-steering correction (gradually adjusted toward target each tick)
    double airSteerVelX = 0.0;
    boolean fixedAirArc = false;

    public double airSteerVelocityX() {
        return airSteerVelX;
    }

    public void setAirSteerVelocityX(double airSteerVelocityX) {
        airSteerVelX = airSteerVelocityX;
    }

    public boolean fixedAirArc() {
        return fixedAirArc;
    }

    public void setFixedAirArc(boolean fixedAirArc) {
        this.fixedAirArc = fixedAirArc;
    }

    // Movement intent
    boolean climbUpIntent = false;

    public boolean climbUpIntent() {
        return climbUpIntent;
    }

    public void setClimbUpIntent(boolean climbUpIntent) {
        this.climbUpIntent = climbUpIntent;
    }

    public Rope blockedRopeGrab() {
        return blockedRopeGrab;
    }

    public void setBlockedRopeGrab(Rope rope) {
        blockedRopeGrab = rope;
    }

    public void clearBlockedRopeGrab() {
        blockedRopeGrab = null;
    }

    int ropeGrabCooldownMs = 0;

    public int ropeGrabCooldownMs() {
        return ropeGrabCooldownMs;
    }

    public void setRopeGrabCooldownMs(int ropeGrabCooldownMs) {
        this.ropeGrabCooldownMs = ropeGrabCooldownMs;
    }

    // Down-jump: true when crouch was shown last tick, jump fires this tick
    boolean downJumpPending = false;
    long downJumpGracePeriodMS = 0;

    public boolean downJumpPending() {
        return downJumpPending;
    }

    public long downJumpGracePeriodMs() {
        return downJumpGracePeriodMS;
    }

    public void setDownJumpPending(boolean downJumpPending) {
        this.downJumpPending = downJumpPending;
    }

    public void setDownJumpGracePeriodMs(long downJumpGracePeriodMs) {
        this.downJumpGracePeriodMS = downJumpGracePeriodMs;
    }

    boolean ropeEntryPending = false;

    public boolean ropeEntryPending() {
        return ropeEntryPending;
    }

    Rope ropeEntryRope = null;
    int ropeEntryY = 0;

    public Rope ropeEntryRope() {
        return ropeEntryRope;
    }

    public int ropeEntryY() {
        return ropeEntryY;
    }

    public void queueRopeEntry(Rope rope, int y) {
        ropeEntryPending = true;
        ropeEntryRope = rope;
        ropeEntryY = y;
    }

    public void clearRopeEntry() {
        ropeEntryPending = false;
        ropeEntryRope = null;
        ropeEntryY = 0;
    }

    // Grind mode
    volatile boolean grinding = false;
    Monster grindTarget = null;
    long nextGrindTargetSearchAtMs = 0L;
    private final AgentCombatCooldownState combatCooldownState = new AgentCombatCooldownState();

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
        return combatCooldownState.attackCooldownMs();
    }

    public void setAttackCooldownMs(int attackCooldownMs) {
        combatCooldownState.setAttackCooldownMs(attackCooldownMs);
    }

    public int moveWindowMs() {
        return combatCooldownState.moveWindowMs();
    }

    public void setMoveWindowMs(int moveWindowMs) {
        combatCooldownState.setMoveWindowMs(moveWindowMs);
    }

    // Skill cache
    private int cachedSkillJob = -1;
    private int cachedSkillLevel = -1;
    private int cachedSkillSignature = 0;
    private final List<Integer> attackSkillIds = new ArrayList<>();
    private int attackSkillId = 0;
    private int aoeSkillId = 0;
    private int aoeSkillMobs = 1;
    private int healSkillId = 0;
    private final List<Integer> buffSkillIds = new ArrayList<>();
    // Summon skills (Phoenix, Puppet, Beholder, ...) classified into their own bucket: they are
    // NOT rebuffable (the bot has no summon-cast path that sends a spawn position, so casting them
    // via the buff loop only burns MP without spawning the creature). Held here for a future
    // place/condition-gated summon caster; the generic rebuff loop ignores this list.
    private final List<Integer> summonSkillIds = new ArrayList<>();
    private final Map<Integer, Long> nextBuffAt = new HashMap<>();
    private final Map<Integer, Long> nextSupportBuffAt = new HashMap<>();
    long nextSupportHealAt = 0L;
    private boolean supportHealsEnabled = true;
    private boolean skillBuffsEnabled = true;

    public boolean supportHealsEnabled() {
        return supportHealsEnabled;
    }

    public void setSupportHealsEnabled(boolean supportHealsEnabled) {
        this.supportHealsEnabled = supportHealsEnabled;
    }

    public boolean skillBuffsEnabled() {
        return skillBuffsEnabled;
    }

    public void setSkillBuffsEnabled(boolean skillBuffsEnabled) {
        this.skillBuffsEnabled = skillBuffsEnabled;
    }

    public long nextBuffAt(int skillId) {
        return nextBuffAt.getOrDefault(skillId, 0L);
    }

    public void ensureNextBuffAt(int skillId, long nextAt) {
        nextBuffAt.putIfAbsent(skillId, nextAt);
    }

    public void setNextBuffAt(int skillId, long nextAt) {
        nextBuffAt.put(skillId, nextAt);
    }

    public long nextSupportBuffAt(int skillId) {
        return nextSupportBuffAt.getOrDefault(skillId, 0L);
    }

    public void setNextSupportBuffAt(int skillId, long nextAt) {
        nextSupportBuffAt.put(skillId, nextAt);
    }

    public boolean skillCacheMatches(int jobId, int level, int signature) {
        return cachedSkillJob == jobId
                && cachedSkillLevel == level
                && cachedSkillSignature == signature;
    }

    public void resetSkillCache(int jobId, int level, int signature) {
        cachedSkillJob = jobId;
        cachedSkillLevel = level;
        cachedSkillSignature = signature;
        attackSkillId = 0;
        aoeSkillId = 0;
        aoeSkillMobs = 1;
        attackSkillIds.clear();
        healSkillId = 0;
        buffSkillIds.clear();
        summonSkillIds.clear();
    }

    public List<Integer> attackSkillIds() {
        return attackSkillIds;
    }

    public void addAttackSkillId(int skillId) {
        attackSkillIds.add(skillId);
    }

    public int attackSkillId() {
        return attackSkillId;
    }

    public void setAttackSkillId(int attackSkillId) {
        this.attackSkillId = attackSkillId;
    }

    public int aoeSkillId() {
        return aoeSkillId;
    }

    public int aoeSkillMobs() {
        return aoeSkillMobs;
    }

    public void setAoeSkill(int skillId, int mobCount) {
        aoeSkillId = skillId;
        aoeSkillMobs = mobCount;
    }

    public int healSkillId() {
        return healSkillId;
    }

    public void setHealSkillId(int healSkillId) {
        this.healSkillId = healSkillId;
    }

    public List<Integer> buffSkillIds() {
        return buffSkillIds;
    }

    public void addBuffSkillId(int skillId) {
        buffSkillIds.add(skillId);
    }

    public List<Integer> summonSkillIds() {
        return summonSkillIds;
    }

    public void addSummonSkillId(int skillId) {
        summonSkillIds.add(skillId);
    }

    private final AgentAmmoSupplyState ammoSupplyState = new AgentAmmoSupplyState();

    public boolean noAmmo() {
        return ammoSupplyState.noAmmo();
    }

    public void setNoAmmo(boolean noAmmo) {
        ammoSupplyState.setNoAmmo(noAmmo);
    }

    public boolean ammoWarnSent() {
        return ammoSupplyState.warnSent();
    }

    public void setAmmoWarnSent(boolean ammoWarnSent) {
        ammoSupplyState.setWarnSent(ammoWarnSent);
    }
    boolean degenAttackDone = false; // force retreat after an accidental close-range hit
    private long retreatHoldUntilMs = 0L; // hysteresis: lock the local retreat goal for a short window
    private Point retreatHoldPos = null;  // the locked retreat target — reused while hold is active
    private int breakoutDirection = 0;    // -1/+1 committed escape side while surrounded, 0 = not breaking out
    private long breakoutUntilMs = 0L;    // hard safety timeout for the surround-breakout commitment
    private Point aoeRepositionAnchor = null; // committed AoE sweet-spot to walk to before firing, null = not repositioning
    private long aoeRepositionDeadlineMs = 0L; // bounded-chase timeout for the AoE reposition commitment
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

    private final AgentShopState shopState = new AgentShopState();

    public AgentShopState shopState() {
        return shopState;
    }

    public boolean shopVisitPending() {
        return shopState.visitPending();
    }

    public boolean shopSequenceActive() {
        return shopState.sequenceActive();
    }

    public Point shopNpcPos() {
        return shopState.npcPosition();
    }

    public Point shopTargetPos() {
        return shopState.targetPosition();
    }

    public Point activeShopTargetPos() {
        return shopState.activeTargetPosition();
    }

    public int shopApproachDelayMs() {
        return shopState.approachDelayMs();
    }

    public void setShopApproachDelayMs(int delayMs) {
        shopState.setApproachDelayMs(delayMs);
    }

    public long shopVisitStartedAtMs() {
        return shopState.visitStartedAtMs();
    }

    public long shopSequenceStartedAtMs() {
        return shopState.sequenceStartedAtMs();
    }

    public boolean shopSellTrashPending() {
        return shopState.sellTrashPending();
    }

    public void setShopSellTrashPending(boolean pending) {
        shopState.setSellTrashPending(pending);
    }

    public void startShopVisit(Point npcPosition, Point targetPosition, int approachDelayMs, long startedAtMs) {
        shopState.startVisit(npcPosition, targetPosition, approachDelayMs, startedAtMs);
    }

    public void markShopSequenceActive(long startedAtMs) {
        shopState.markSequenceActive(startedAtMs);
    }

    public boolean stuckNearShopNpc(Point botPosition, long nowMs, long fallbackMs, int moveTolerancePx,
                                    int arriveDistance) {
        return shopState.stuckNearNpc(botPosition, nowMs, fallbackMs, moveTolerancePx, arriveDistance);
    }

    public void clearShopState() {
        shopState.clear();
    }
    // bumped whenever a new player directive resets scripted state (follow/stop/move/farm/patrol/grind);
    // background batches (Maker crafting / disassembly) capture it and self-interrupt when it changes
    private final AgentScriptTaskQueueState scriptTaskQueueState = new AgentScriptTaskQueueState();
    private final AgentDeathState deathState = new AgentDeathState();

    public AgentDeathState deathState() {
        return deathState;
    }

    public long deadUntilMs() {
        return deathState.deadUntilMs();
    }

    public void setDeadUntilMs(long deadUntilMs) {
        deathState.setDeadUntilMs(deadUntilMs);
    }

    public void clearDeadUntilMs() {
        deathState.clear();
    }

    public int mobHitCooldownMs() {
        return combatCooldownState.mobHitCooldownMs();
    }

    public void setMobHitCooldownMs(int mobHitCooldownMs) {
        combatCooldownState.setMobHitCooldownMs(mobHitCooldownMs);
    }

    private final AgentPortalCooldownState portalCooldownState = new AgentPortalCooldownState();

    public AgentPortalCooldownState portalCooldownState() {
        return portalCooldownState;
    }

    public long portalUseCooldownUntilMs() {
        return portalCooldownState.useCooldownUntilMs();
    }

    public void setPortalUseCooldownUntilMs(long portalUseCooldownUntilMs) {
        portalCooldownState.setUseCooldownUntilMs(portalUseCooldownUntilMs);
    }
    public AgentCombatCooldownState combatCooldownState() {
        return combatCooldownState;
    }

    public long alertedUntilMs() {
        return combatCooldownState.alertedUntilMs();
    }

    public void setAlertedUntilMs(long alertedUntilMs) {
        combatCooldownState.setAlertedUntilMs(alertedUntilMs);
    }

    public boolean alertResetScheduled() {
        return combatCooldownState.alertResetScheduled();
    }

    public void setAlertResetScheduled(boolean alertResetScheduled) {
        combatCooldownState.setAlertResetScheduled(alertResetScheduled);
    }

    private final AgentLeaderActivityState leaderActivityState = new AgentLeaderActivityState();
    private final AgentBuildState buildState = new AgentBuildState();

    public AgentLeaderActivityState leaderActivityState() {
        return leaderActivityState;
    }

    public boolean isGrinding() { return grinding; }
    public boolean isFollowing() { return following; }
    public int followTargetId() { return followTargetId; }
    public void setGrinding(boolean grinding) { this.grinding = grinding; }
    public void setFollowing(boolean following) { this.following = following; }
    public void setFollowTargetId(int followTargetId) { this.followTargetId = followTargetId; }
    public Character bot() { return bot; }
    public Character owner() { return owner; }
    public void setOwner(Character owner) { this.owner = owner; }
    public String lastOwnerCommand() { return leaderActivityState.lastCommand(); }
    public long lastOwnerCommandAtMs() { return leaderActivityState.lastCommandAtMs(); }
    public void recordLastOwnerCommand(String command, long commandAtMs) {
        leaderActivityState.recordLastCommand(command, commandAtMs);
    }
    public AgentBuildState buildState() {
        return buildState;
    }
    public AgentBuildService.ApBuild apBuild() { return buildState.apBuild(); }
    public void setApBuild(AgentBuildService.ApBuild apBuild) { buildState.setApBuild(apBuild); }
    public void clearApBuildPromptState() {
        buildState.clearApBuildPromptState();
    }
    public void markApPromptSent() { buildState.markApPromptSent(); }
    public void setSpVariant(String spVariant) { buildState.setSpVariant(spVariant); }
    public boolean apPromptSent() { return buildState.apPromptSent(); }
    public String spVariant() { return buildState.spVariant(); }
    public boolean spVariantPromptSent() { return buildState.spVariantPromptSent(); }
    public void markSpVariantPromptSent() { buildState.markSpVariantPromptSent(); }
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
    private final AgentMobTouchState mobTouchState = new AgentMobTouchState();

    public AgentMobTouchState mobTouchState() {
        return mobTouchState;
    }

    public Point lastMobTouchCheckPos() {
        return mobTouchState.lastCheckPosition();
    }

    public int lastMobTouchMapId() {
        return mobTouchState.lastCheckMapId();
    }

    public void rememberMobTouchCheck(Point position, int mapId) {
        mobTouchState.rememberCheck(position, mapId);
    }

    // Loot and potions
    private final AgentPotionSupplyState potionSupplyState = new AgentPotionSupplyState();
    private final AgentInventoryCooldownState inventoryCooldownState = new AgentInventoryCooldownState();

    public int potCheckTimerMs() {
        return potionSupplyState.potCheckTimerMs();
    }

    public void setPotCheckTimerMs(int potCheckTimerMs) {
        potionSupplyState.setPotCheckTimerMs(potCheckTimerMs);
    }

    public int mpRecoveryTimerMs() {
        return potionSupplyState.mpRecoveryTimerMs();
    }

    public void setMpRecoveryTimerMs(int mpRecoveryTimerMs) {
        potionSupplyState.setMpRecoveryTimerMs(mpRecoveryTimerMs);
    }

    public int invFullWarnCooldownMs() {
        return inventoryCooldownState.inventoryFullWarnCooldownMs();
    }

    public void setInvFullWarnCooldownMs(int invFullWarnCooldownMs) {
        inventoryCooldownState.setInventoryFullWarnCooldownMs(invFullWarnCooldownMs);
    }

    public AgentInventoryCooldownState inventoryCooldownState() {
        return inventoryCooldownState;
    }

    public AgentPotionSupplyState potionSupplyState() {
        return potionSupplyState;
    }

    public AgentAmmoSupplyState ammoSupplyState() {
        return ammoSupplyState;
    }

    public boolean potShareRequestedHp() {
        return potionSupplyState.hpShareRequested();
    }

    public void setPotShareRequestedHp(boolean potShareRequestedHp) {
        potionSupplyState.setHpShareRequested(potShareRequestedHp);
    }

    public boolean potShareRequestedMp() {
        return potionSupplyState.mpShareRequested();
    }

    public void setPotShareRequestedMp(boolean potShareRequestedMp) {
        potionSupplyState.setMpShareRequested(potShareRequestedMp);
    }

    public boolean ammoShareRequested() {
        return ammoSupplyState.shareRequested();
    }

    public void setAmmoShareRequested(boolean ammoShareRequested) {
        ammoSupplyState.setShareRequested(ammoShareRequested);
    }

    public int jobPromptSent() {
        return buildState.jobPromptSent();
    }

    public void setJobPromptSent(int jobPromptSent) {
        buildState.setJobPromptSent(jobPromptSent);
    }

    public int lastKnownLevel() {
        return buildState.lastKnownLevel();
    }

    public void setLastKnownLevel(int lastKnownLevel) {
        buildState.setLastKnownLevel(lastKnownLevel);
    }

    // Reply channel — tracks the chat channel the last owner command arrived on.
    // Bot replies are routed to this channel until the next command changes it.
    volatile AgentReplyChannel replyChannel = AgentReplyChannel.MAP;

    private final AgentPendingActionState pendingActionState = new AgentPendingActionState();

    public AgentPendingActionState pendingActionState() {
        return pendingActionState;
    }

    // Pending two-step action
    public String pendingAction() { return pendingActionState.pendingAction(); }
    public void setPendingAction(String pendingAction) { pendingActionState.setPendingAction(pendingAction); }
    public void clearPendingAction() { pendingActionState.clearPendingAction(); }
    public String pendingDropCategory() { return pendingActionState.pendingDropCategory(); }
    public void setPendingDropCategory(String pendingDropCategory) { pendingActionState.setPendingDropCategory(pendingDropCategory); }
    public void clearPendingDropCategory() { pendingActionState.clearPendingDropCategory(); }
    private final AgentPendingLootOfferState pendingLootOfferState = new AgentPendingLootOfferState();
    private final AgentTradeRetryState tradeRetryState = new AgentTradeRetryState();

    public AgentPendingLootOfferState pendingLootOfferState() {
        return pendingLootOfferState;
    }

    public Item pendingLootOfferItem() { return pendingLootOfferState.item(); }

    public int pendingLootOfferRecipientId() {
        return pendingLootOfferState.recipientId();
    }

    public long pendingLootOfferExpiresAt() {
        return pendingLootOfferState.expiresAt();
    }

    public boolean pendingLootOfferBotRequesting() {
        return pendingLootOfferState.botRequesting();
    }

    public void setPendingLootOffer(Item item, int recipientId, long expiresAt, boolean botRequesting) {
        pendingLootOfferState.set(item, recipientId, expiresAt, botRequesting);
    }

    public void clearPendingLootOffer() {
        setPendingLootOffer(null, 0, 0L, false);
    }

    public void clearPendingLootOfferForAcceptedTransfer() {
        pendingLootOfferState.clearAcceptedTransfer();
    }

    public void clearPendingLootOfferItem() {
        pendingLootOfferState.clearItem();
    }

    public int lootInhibitMs() {
        return inventoryCooldownState.lootInhibitMs();
    }

    public void setLootInhibitMs(int lootInhibitMs) {
        inventoryCooldownState.setLootInhibitMs(lootInhibitMs);
    }

    // Bot-initiated trade retry: when a pot-share / ammo-share / loot-offer is blocked
    // because the sender or recipient is already in a trade, the attempt is stored here
    // and re-fired once the sender's trade clears and the delay expires.
    public AgentTradeRetryState tradeRetryState() {
        return tradeRetryState;
    }

    public Runnable pendingBotTradeRetry() {
        return tradeRetryState.retry();
    }

    public void setPendingBotTradeRetry(Runnable pendingBotTradeRetry) {
        tradeRetryState.setRetry(pendingBotTradeRetry);
    }

    public int pendingBotTradeRetryMs() {
        return tradeRetryState.delayMs();
    }

    public void setPendingBotTradeRetryMs(int pendingBotTradeRetryMs) {
        tradeRetryState.setDelayMs(pendingBotTradeRetryMs);
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
    private final AgentMessageQueueState messageQueueState = new AgentMessageQueueState();

    public AgentMessageQueueState messageQueueState() {
        return messageQueueState;
    }

    public Deque<AgentQueuedMessage> messageQueue() {
        return messageQueueState.queue();
    }

    public boolean isMessageSending() {
        return messageQueueState.isSending();
    }

    public void setMessageSending(boolean msgSending) {
        messageQueueState.setSending(msgSending);
    }

    public AgentScriptTaskQueueState scriptTaskQueueState() {
        return scriptTaskQueueState;
    }

    public int activityEpoch() {
        return scriptTaskQueueState.activityEpoch();
    }

    public int bumpActivityEpoch() {
        return scriptTaskQueueState.bumpActivityEpoch();
    }

    public void addScriptTask(AgentTask task) {
        scriptTaskQueueState.addTask(task);
    }

    public AgentTask activeScriptTask() {
        return scriptTaskQueueState.activeTask();
    }

    public void setActiveScriptTask(AgentTask activeScriptTask) {
        scriptTaskQueueState.setActiveTask(activeScriptTask);
    }

    public AgentTask pollScriptTask() {
        return scriptTaskQueueState.pollTask();
    }

    public boolean hasScriptTasks() {
        return scriptTaskQueueState.hasTasks();
    }

    public void clearScriptTasks() {
        scriptTaskQueueState.clearTasks();
    }

    public Point ownerAfkPosition() {
        return leaderActivityState.afkPosition();
    }

    public void setOwnerAfkPosition(Point ownerAfkPos) {
        leaderActivityState.setAfkPosition(ownerAfkPos);
    }

    public long ownerAfkSinceMs() {
        return leaderActivityState.afkSinceMs();
    }

    public void setOwnerAfkSinceMs(long ownerAfkSinceMs) {
        leaderActivityState.setAfkSinceMs(ownerAfkSinceMs);
    }

    public boolean ownerWasAfk() {
        return leaderActivityState.wasAfk();
    }

    public void setOwnerWasAfk(boolean ownerWasAfk) {
        leaderActivityState.setWasAfk(ownerWasAfk);
    }

    public long ownerOfflineOrDeadSinceMs() {
        return leaderActivityState.offlineOrDeadSinceMs();
    }

    public void setOwnerOfflineOrDeadSinceMs(long ownerOfflineOrDeadSinceMs) {
        leaderActivityState.setOfflineOrDeadSinceMs(ownerOfflineOrDeadSinceMs);
    }

    public boolean ownerReturnedToTown() {
        return leaderActivityState.returnedToTown();
    }

    public void setOwnerReturnedToTown(boolean ownerReturnedToTown) {
        leaderActivityState.setReturnedToTown(ownerReturnedToTown);
    }

    public boolean ownerAwaySafeMode() {
        return leaderActivityState.awaySafeMode();
    }

    public void setOwnerAwaySafeMode(boolean ownerAwaySafeMode) {
        leaderActivityState.setAwaySafeMode(ownerAwaySafeMode);
    }

    // Foothold index, rebuilt on map change
    private final AgentMapTrackingState mapTrackingState = new AgentMapTrackingState();

    public AgentMapTrackingState mapTrackingState() {
        return mapTrackingState;
    }

    public int lastMapId() {
        return mapTrackingState.lastMapId();
    }

    public Map<Integer, Foothold> footholdIndex() {
        return mapTrackingState.footholdIndex();
    }

    public void setMapTracking(int mapId, Map<Integer, Foothold> footholdIndex) {
        mapTrackingState.setMapTracking(mapId, footholdIndex);
    }

    // Human-like spacing and stagger — assigned at registration based on bot index
    private final AgentFormationOffsetState formationOffsetState = new AgentFormationOffsetState();

    public int followOffsetX() {
        return formationOffsetState.followOffsetX();
    }

    public void setFollowOffsetX(int followOffsetX) {
        formationOffsetState.setFollowOffsetX(followOffsetX);
    }

    public AgentFormationOffsetState formationOffsetState() {
        return formationOffsetState;
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
    int patrolRegionId = -1;    // AgentNavigationGraph.Region id; -1 = inactive
    int patrolMapId = -1;
    Point patrolWanderTarget = null;

    private final AgentBuffState buffState = new AgentBuffState();
    private final AgentUpgradeOfferState upgradeOfferState = new AgentUpgradeOfferState();

    public AgentBuffState buffState() {
        return buffState;
    }

    public AgentUpgradeOfferState upgradeOfferState() {
        return upgradeOfferState;
    }

    public long lastBuffScanMs() {
        return buffState.lastConsumableScanMs();
    }

    public void setLastBuffScanMs(long lastBuffScanMs) {
        buffState.setLastConsumableScanMs(lastBuffScanMs);
    }

    public long lastBuffActionAtMs() {
        return buffState.lastConsumableActionAtMs();
    }

    public String lastBuffActionSummary() {
        return buffState.lastConsumableActionSummary();
    }

    public void setLastBuffAction(long atMs, String summary) {
        buffState.rememberConsumableAction(atMs, summary);
    }

    public boolean buffConsumablesEnabled() {
        return buffState.consumablesEnabled();
    }

    public void setBuffConsumablesEnabled(boolean buffConsumablesEnabled) {
        buffState.setConsumablesEnabled(buffConsumablesEnabled);
    }

    public boolean buffCheapMode() {
        return buffState.cheapMode();
    }

    public void setBuffCheapMode(boolean buffCheapMode) {
        buffState.setCheapMode(buffCheapMode);
    }

    public void resetLastBuffScan() {
        buffState.resetLastConsumableScan();
    }

    public void setProactiveUpgradeOffers(boolean proactiveUpgradeOffers) {
        upgradeOfferState.setProactiveUpgradeOffers(proactiveUpgradeOffers);
    }

    public boolean proactiveUpgradeOffers() {
        return upgradeOfferState.proactiveUpgradeOffers();
    }

    public long lastSkillBuffActionAtMs() {
        return buffState.lastSkillActionAtMs();
    }

    public String lastSkillBuffActionSummary() {
        return buffState.lastSkillActionSummary();
    }

    public void rememberSkillBuffAction(long atMs, String summary) {
        buffState.rememberSkillAction(atMs, summary);
    }

    // Party-quest state (one slot per PQ type; null = not in that PQ)
    public server.agents.capabilities.partyquest.kpq.AgentKpqState kpq = new server.agents.capabilities.partyquest.kpq.AgentKpqState();

    public void resetKpqStage5Claimed() {
        kpq.stage5Claimed = false;
    }

    public AgentScriptRuntimeState script = new AgentScriptRuntimeState();

    // Equips received from the owner during the current trade session.
    // Cleared when that trade session finishes or is cancelled.
    Set<Item> ownerGivenItems = Collections.newSetFromMap(new IdentityHashMap<>());

    public Set<Item> ownerGivenItems() {
        return ownerGivenItems;
    }

    private final AgentNavigationDebugState navigationDebugState = new AgentNavigationDebugState();
    private final AgentNavigationEdgeState navigationEdgeState = new AgentNavigationEdgeState();
    private final AgentNavigationTargetState navigationTargetState = new AgentNavigationTargetState();
    private final AgentMovementBroadcastState movementBroadcastState = new AgentMovementBroadcastState();
    private final AgentMovementStuckState movementStuckState = new AgentMovementStuckState();
    private final AgentOwnerMotionState ownerMotionState = new AgentOwnerMotionState();
    private final AgentTickFailureState tickFailureState = new AgentTickFailureState();
    private final AgentTickState tickState = new AgentTickState();

    // Cached movement state shared across ticks

    public boolean hasActiveNavigationEdge() {
        return navigationEdgeState.hasActiveEdge();
    }

    public Object activeNavigationEdge() {
        return navigationEdgeState.activeEdge();
    }

    public void setActiveNavigationEdge(Object edge) {
        navigationEdgeState.setActiveEdge(edge);
    }

    public void clearActiveNavigationEdge() {
        navigationEdgeState.clearActiveEdge();
    }

    public int observedOwnerStepX() {
        return ownerMotionState.observedOwnerStepX();
    }

    public int observedOwnerStepY() {
        return ownerMotionState.observedOwnerStepY();
    }

    public void setObservedOwnerStep(int stepX, int stepY) {
        ownerMotionState.setObservedOwnerStep(stepX, stepY);
    }
    AgentFidgetMode fidgetMode = AgentFidgetMode.NONE;
    AgentFidgetTrigger fidgetTrigger = AgentFidgetTrigger.NONE;
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

    public AgentFidgetMode fidgetMode() {
        return fidgetMode;
    }

    public boolean hasActiveFidgetMode() {
        return fidgetMode != AgentFidgetMode.NONE;
    }

    public AgentFidgetTrigger fidgetTrigger() {
        return fidgetTrigger;
    }

    public long fidgetUntilMs() {
        return fidgetUntilMs;
    }

    public long nextFidgetActionAtMs() {
        return nextFidgetActionAtMs;
    }

    public int fidgetAirSteerDir() {
        return fidgetAirSteerDir;
    }

    public int fidgetJumpDir() {
        return fidgetJumpDir;
    }

    public int fidgetMoveDir() {
        return fidgetMoveDir;
    }

    public boolean fidgetSpamAirSteer() {
        return fidgetSpamAirSteer;
    }

    public int fidgetActionBaseDelayMs() {
        return fidgetActionBaseDelayMs;
    }

    public long nextFidgetJumpAtMs() {
        return nextFidgetJumpAtMs;
    }

    public Point fidgetOriginPos() {
        return fidgetOriginPos == null ? null : new Point(fidgetOriginPos);
    }

    public long nextFidgetVisualAtMs() {
        return nextFidgetVisualAtMs;
    }

    public long nextFidgetAtMs() {
        return nextFidgetAtMs;
    }

    public long nextIdleFidgetRollAtMs() {
        return nextIdleFidgetRollAtMs;
    }

    public boolean crouching() {
        return crouching;
    }

    public void setCrouching(boolean crouching) {
        this.crouching = crouching;
    }

    public void clearFidgetState() {
        fidgetMode = AgentFidgetMode.NONE;
        fidgetTrigger = AgentFidgetTrigger.NONE;
        fidgetUntilMs = 0L;
        nextFidgetActionAtMs = 0L;
        fidgetAirSteerDir = 0;
        fidgetJumpDir = 0;
        fidgetMoveDir = 0;
        fidgetSpamAirSteer = false;
        fidgetActionBaseDelayMs = 0;
        nextFidgetJumpAtMs = 0L;
        fidgetOriginPos = null;
        nextFidgetVisualAtMs = 0L;
    }

    public void startFidgetState(AgentFidgetMode mode,
                                 AgentFidgetTrigger trigger,
                                 long untilMs,
                                 long nowMs,
                                 int airSteerDir,
                                 boolean spamAirSteer,
                                 int actionBaseDelayMs,
                                 Point originPos,
                                 long nextVisualAtMs,
                                 long nextFidgetAtMs) {
        fidgetMode = mode;
        fidgetTrigger = trigger;
        fidgetUntilMs = untilMs;
        nextFidgetActionAtMs = nowMs;
        fidgetAirSteerDir = airSteerDir;
        fidgetJumpDir = airSteerDir == 0 ? 1 : airSteerDir;
        fidgetMoveDir = airSteerDir;
        fidgetSpamAirSteer = spamAirSteer;
        fidgetActionBaseDelayMs = actionBaseDelayMs;
        nextFidgetJumpAtMs = nowMs;
        fidgetOriginPos = originPos == null ? null : new Point(originPos);
        nextFidgetVisualAtMs = nextVisualAtMs;
        this.nextFidgetAtMs = nextFidgetAtMs;
    }

    public void setNextIdleFidgetRollAtMs(long nextIdleFidgetRollAtMs) {
        this.nextIdleFidgetRollAtMs = nextIdleFidgetRollAtMs;
    }

    public void setNextFidgetAtMs(long nextFidgetAtMs) {
        this.nextFidgetAtMs = nextFidgetAtMs;
    }

    public void setFidgetAirSteerDir(int fidgetAirSteerDir) {
        this.fidgetAirSteerDir = fidgetAirSteerDir;
    }

    public void setNextFidgetActionAtMs(long nextFidgetActionAtMs) {
        this.nextFidgetActionAtMs = nextFidgetActionAtMs;
    }

    public void setFidgetJumpDir(int fidgetJumpDir) {
        this.fidgetJumpDir = fidgetJumpDir;
    }

    public void setNextFidgetJumpAtMs(long nextFidgetJumpAtMs) {
        this.nextFidgetJumpAtMs = nextFidgetJumpAtMs;
    }

    public void setFidgetMoveDir(int fidgetMoveDir) {
        this.fidgetMoveDir = fidgetMoveDir;
    }

    public void setNextFidgetVisualAtMs(long nextFidgetVisualAtMs) {
        this.nextFidgetVisualAtMs = nextFidgetVisualAtMs;
    }

    public long nextGearSuggestionAt() {
        return upgradeOfferState.nextGearSuggestionAt();
    }

    public void setNextGearSuggestionAt(long nextGearSuggestionAt) {
        upgradeOfferState.setNextGearSuggestionAt(nextGearSuggestionAt);
    }

    public boolean spawnUpgradeCheckDone() {
        return upgradeOfferState.spawnUpgradeCheckDone();
    }

    public void setSpawnUpgradeCheckDone(boolean spawnUpgradeCheckDone) {
        upgradeOfferState.setSpawnUpgradeCheckDone(spawnUpgradeCheckDone);
    }

    public boolean hasRequestedUpgradeItem(int itemId) {
        return upgradeOfferState.hasRequestedUpgradeItem(itemId);
    }

    public void rememberRequestedUpgradeItem(int itemId) {
        upgradeOfferState.rememberRequestedUpgradeItem(itemId);
    }

    private final AgentScrollReactionState scrollReactionState = new AgentScrollReactionState();

    public AgentNavigationDebugState navigationDebugState() {
        return navigationDebugState;
    }

    public AgentNavigationEdgeState navigationEdgeState() {
        return navigationEdgeState;
    }

    public AgentNavigationTargetState navigationTargetState() {
        return navigationTargetState;
    }

    public AgentOwnerMotionState ownerMotionState() {
        return ownerMotionState;
    }

    public AgentMovementBroadcastState movementBroadcastState() {
        return movementBroadcastState;
    }

    public AgentMovementStuckState movementStuckState() {
        return movementStuckState;
    }

    public AgentTickState tickState() {
        return tickState;
    }

    public AgentTickFailureState tickFailureState() {
        return tickFailureState;
    }

    public AgentPathLogger pathLogger() {
        return navigationDebugState.pathLogger();
    }

    public void setPathLogger(AgentPathLogger pathLogger) {
        navigationDebugState.setPathLogger(pathLogger);
    }

    public void clearPathLogger() {
        navigationDebugState.clearPathLogger();
    }

    public String lastNavDecision() {
        return navigationDebugState.lastDecision();
    }

    public void setLastNavDecision(String lastNavDecision) {
        navigationDebugState.setLastDecision(lastNavDecision);
    }

    public String lastEdgeBlockReason() {
        return navigationDebugState.lastEdgeBlockReason();
    }

    public void setLastEdgeBlockReason(String lastEdgeBlockReason) {
        navigationDebugState.setLastEdgeBlockReason(lastEdgeBlockReason);
    }

    public boolean graphWarmupFallback() {
        return navigationDebugState.graphWarmupFallback();
    }

    public void setGraphWarmupFallback(boolean graphWarmupFallback) {
        navigationDebugState.setGraphWarmupFallback(graphWarmupFallback);
    }

    public Point navTargetPos() {
        return navigationTargetState.position();
    }

    public void setNavTargetPos(Point navTargetPos) {
        navigationTargetState.setPosition(navTargetPos);
    }

    public int navTargetRegionId() {
        return navigationTargetState.regionId();
    }

    public void setNavTargetRegionId(int navTargetRegionId) {
        navigationTargetState.setRegionId(navTargetRegionId);
    }

    public boolean navPreciseTarget() {
        return navigationTargetState.precise();
    }

    public void setNavPreciseTarget(boolean navPreciseTarget) {
        navigationTargetState.setPrecise(navPreciseTarget);
    }

    public boolean matchesNavJumpLaunchEdge(Object edge) {
        return navigationEdgeState.matchesJumpLaunchEdge(edge);
    }

    public boolean hasNavJumpLaunchEdge() {
        return navigationEdgeState.hasJumpLaunchEdge();
    }

    public int navJumpLaunchX() {
        return navigationEdgeState.jumpLaunchX();
    }

    public void setNavJumpLaunch(Object navJumpLaunchEdge, int navJumpLaunchX) {
        navigationEdgeState.setJumpLaunch(navJumpLaunchEdge, navJumpLaunchX);
    }

    public long pendingGearPromptAt() {
        return upgradeOfferState.pendingGearPromptAt();
    }

    public double recentScrollReactionLoad() {
        return scrollReactionState.recentLoad();
    }

    public void setRecentScrollReactionLoad(double recentScrollReactionLoad) {
        scrollReactionState.setRecentLoad(recentScrollReactionLoad);
    }

    public long lastScrollReactionObservedAtMs() {
        return scrollReactionState.lastObservedAtMs();
    }

    public void setLastScrollReactionObservedAtMs(long lastScrollReactionObservedAtMs) {
        scrollReactionState.setLastObservedAtMs(lastScrollReactionObservedAtMs);
    }

    public long nextScrollReactionAtMs() {
        return scrollReactionState.nextReactionAtMs();
    }

    public void setNextScrollReactionAtMs(long nextScrollReactionAtMs) {
        scrollReactionState.setNextReactionAtMs(nextScrollReactionAtMs);
    }

    public AgentScrollReactionState scrollReactionState() {
        return scrollReactionState;
    }

    public Map<Integer, AgentScrollReactionState.StreakState> scrollReactionStreaksByScroller() {
        return scrollReactionState.streaksByScroller();
    }

    public long nextScrollReactionStreakPruneAtMs() {
        return scrollReactionState.nextStreakPruneAtMs();
    }

    public void setNextScrollReactionStreakPruneAtMs(long nextScrollReactionStreakPruneAtMs) {
        scrollReactionState.setNextStreakPruneAtMs(nextScrollReactionStreakPruneAtMs);
    }

    public void setPendingGearPromptAt(long pendingGearPromptAt) {
        upgradeOfferState.reserveGearPrompt(pendingGearPromptAt);
    }

    public Point lastOwnerPosition() {
        return ownerMotionState.lastOwnerPosition();
    }

    public void setLastOwnerPosition(Point lastOwnerPos) {
        ownerMotionState.setLastOwnerPosition(lastOwnerPos);
    }

    public boolean lastTickWasAi() {
        return tickState.lastTickWasAi();
    }

    public long lastTickAtMs() {
        return tickState.lastTickAtMs();
    }

    public void recordTick(boolean aiTick, long tickAtMs) {
        tickState.recordTick(aiTick, tickAtMs);
    }

    public long lastHeartbeatAtMs() {
        return tickState.lastHeartbeatAtMs();
    }

    public void setLastHeartbeatAtMs(long lastHeartbeatAtMs) {
        tickState.setLastHeartbeatAtMs(lastHeartbeatAtMs);
    }

    public long nextFollowIdleMovementCheckAtMs() {
        return tickState.nextFollowIdleMovementCheckAtMs();
    }

    public void setNextFollowIdleMovementCheckAtMs(long nextFollowIdleMovementCheckAtMs) {
        tickState.setNextFollowIdleMovementCheckAtMs(nextFollowIdleMovementCheckAtMs);
    }

    public int tickFailureCount() {
        return tickFailureState.failureCount();
    }

    public long tickFailureWindowStartedAtMs() {
        return tickFailureState.windowStartedAtMs();
    }

    public void resetTickFailureWindow(long startedAtMs) {
        tickFailureState.resetWindow(startedAtMs);
    }

    public int incrementTickFailureCount() {
        return tickFailureState.incrementFailureCount();
    }

    public void clearTickFailures() {
        tickFailureState.clear();
    }

    public int stuckMs() {
        return movementStuckState.stuckMs();
    }

    public void setStuckMs(int stuckMs) {
        movementStuckState.setStuckMs(stuckMs);
    }

    public void addStuckMs(int deltaMs) {
        movementStuckState.addStuckMs(deltaMs);
    }

    public int unstuckCooldownMs() {
        return movementStuckState.unstuckCooldownMs();
    }

    public void setUnstuckCooldownMs(int unstuckCooldownMs) {
        movementStuckState.setUnstuckCooldownMs(unstuckCooldownMs);
    }

    public int stuckCheckX() {
        return movementStuckState.stuckCheckX();
    }

    public int stuckCheckY() {
        return movementStuckState.stuckCheckY();
    }

    public boolean hasStuckCheckPosition() {
        return movementStuckState.hasStuckCheckPosition();
    }

    public void setStuckCheckPosition(Point position) {
        movementStuckState.setStuckCheckPosition(position);
    }

    public void clearStuckCheckPosition() {
        movementStuckState.clearStuckCheckPosition();
    }

    // Manual trade: countdown before bot accepts an incoming trade invite (both owner and peer-bot)
    private final AgentManualTradeState manualTradeState = new AgentManualTradeState();

    public AgentManualTradeState manualTradeState() {
        return manualTradeState;
    }

    public int manualTradeAcceptDelayMs() {
        return manualTradeState.acceptDelayMs();
    }

    public void setManualTradeAcceptDelayMs(int manualTradeAcceptDelayMs) {
        manualTradeState.setAcceptDelayMs(manualTradeAcceptDelayMs);
    }

    public Trade manualTradeRef() {
        return manualTradeState.tradeRef();
    }

    public void setManualTradeRef(Trade manualTradeRef) {
        manualTradeState.setTradeRef(manualTradeRef);
    }

    public int manualTradeTimeoutMs() {
        return manualTradeState.timeoutMs();
    }

    public void setManualTradeTimeoutMs(int manualTradeTimeoutMs) {
        manualTradeState.setTimeoutMs(manualTradeTimeoutMs);
    }

    // Movement packet cache so repeated no-op packets are suppressed
    int lastGroundFhId = 0;

    public int lastGroundFhId() {
        return lastGroundFhId;
    }

    public void setLastGroundFhId(int lastGroundFhId) {
        this.lastGroundFhId = lastGroundFhId;
    }

    public boolean movementBroadcastValid() {
        return movementBroadcastState.valid();
    }

    public void setMovementBroadcastValid(boolean movementBroadcastValid) {
        movementBroadcastState.setValid(movementBroadcastValid);
    }

    public int lastBroadcastX() {
        return movementBroadcastState.x();
    }

    public void setLastBroadcastX(int lastBroadcastX) {
        movementBroadcastState.setX(lastBroadcastX);
    }

    public int lastBroadcastY() {
        return movementBroadcastState.y();
    }

    public void setLastBroadcastY(int lastBroadcastY) {
        movementBroadcastState.setY(lastBroadcastY);
    }

    public int lastBroadcastVelX() {
        return movementBroadcastState.velocityX();
    }

    public void setLastBroadcastVelX(int lastBroadcastVelX) {
        movementBroadcastState.setVelocityX(lastBroadcastVelX);
    }

    public int lastBroadcastVelY() {
        return movementBroadcastState.velocityY();
    }

    public void setLastBroadcastVelY(int lastBroadcastVelY) {
        movementBroadcastState.setVelocityY(lastBroadcastVelY);
    }

    public int lastBroadcastStance() {
        return movementBroadcastState.stance();
    }

    public void setLastBroadcastStance(int lastBroadcastStance) {
        movementBroadcastState.setStance(lastBroadcastStance);
    }

    public int lastBroadcastFh() {
        return movementBroadcastState.footholdId();
    }

    public void setLastBroadcastFh(int lastBroadcastFh) {
        movementBroadcastState.setFootholdId(lastBroadcastFh);
    }

    public BotEntry(Character bot, Character owner, ScheduledFuture<?> task) {
        this.bot = bot;
        this.owner = owner;
        this.task = task;
    }

    // Accessors for code outside the server.bots package (e.g. server.agents.capabilities.dialogue.llm).
    // Mutations stay package-private to preserve existing invariants.
    public Character getBot() { return bot; }
    public Character getOwner() { return owner; }
    public AgentReplyChannel getReplyChannel() { return replyChannel; }
    public void setReplyChannel(AgentReplyChannel replyChannel) { this.replyChannel = replyChannel; }
}
