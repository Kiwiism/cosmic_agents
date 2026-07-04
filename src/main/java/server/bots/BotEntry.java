package server.bots;

import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationDebugState;
import server.agents.capabilities.navigation.AgentNavigationEdgeState;
import server.agents.capabilities.navigation.AgentNavigationTargetState;
import server.agents.capabilities.navigation.AgentPortalCooldownState;

import server.agents.capabilities.build.AgentBuildService;
import server.agents.capabilities.build.AgentBuildState;
import server.agents.capabilities.combat.AgentCombatBuffState;
import server.agents.capabilities.combat.AgentBuffState;
import server.agents.capabilities.combat.AgentCombatCooldownState;
import server.agents.capabilities.combat.AgentCombatSkillCacheState;
import server.agents.capabilities.combat.AgentMobTouchState;

import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.movement.AgentGroundTravelState;
import server.agents.capabilities.movement.AgentAirborneSteeringState;
import server.agents.capabilities.movement.AgentClimbState;
import server.agents.capabilities.movement.AgentDownJumpState;
import server.agents.capabilities.movement.AgentMovementInputState;
import server.agents.capabilities.movement.AgentMovementPhysicsState;
import server.agents.capabilities.movement.AgentSwimIntentState;

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
import server.agents.commands.AgentReplyChannelState;
import server.agents.capabilities.dialogue.AgentPendingActionState;
import server.agents.capabilities.inventory.AgentInventoryCooldownState;
import server.agents.capabilities.looting.AgentGrindLootState;
import server.agents.capabilities.social.AgentScrollReactionState;
import server.agents.capabilities.shop.AgentShopState;
import server.agents.capabilities.supplies.AgentAmmoSupplyState;
import server.agents.capabilities.supplies.AgentPotionSupplyState;
import server.agents.capabilities.social.airshow.AgentAirshowState;
import server.agents.capabilities.movement.fidget.AgentFidgetMode;
import server.agents.capabilities.movement.fidget.AgentFidgetState;
import server.agents.capabilities.movement.fidget.AgentFidgetTrigger;
import server.agents.capabilities.trade.AgentPendingLootOfferState;
import server.agents.capabilities.trade.AgentPendingTradeSequenceState;
import server.agents.capabilities.trade.AgentManualTradeState;
import server.agents.capabilities.trade.AgentOwnerGivenTradeItemState;
import server.agents.capabilities.trade.AgentTradeRetryState;
import server.agents.capabilities.trade.AgentUpgradeOfferState;
import server.agents.monitoring.AgentPathLogger;
import server.agents.plans.AgentTask;
import server.agents.plans.AgentScriptTaskQueueState;
import server.agents.plans.AgentScriptRuntimeState;
import server.agents.runtime.AgentFormationOffsetState;
import server.agents.runtime.AgentDeathState;
import server.agents.runtime.AgentFarmAnchorState;
import server.agents.runtime.AgentBreakoutState;
import server.agents.runtime.AgentDegenerateAttackState;
import server.agents.runtime.AgentGrindTargetState;
import server.agents.runtime.AgentGrindWanderState;
import server.agents.runtime.AgentLeaderActivityState;
import server.agents.runtime.AgentMapTrackingState;
import server.agents.runtime.AgentMoveTargetState;
import server.agents.runtime.AgentMovementBroadcastState;
import server.agents.runtime.AgentMovementPhysicsCacheState;
import server.agents.runtime.AgentMovementStuckState;
import server.agents.runtime.AgentAoeRepositionState;
import server.agents.runtime.AgentOwnerMotionState;
import server.agents.runtime.AgentPatrolState;
import server.agents.runtime.AgentRetreatHoldState;
import server.agents.runtime.AgentTickFailureState;
import server.agents.runtime.AgentTickState;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

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
    private final AgentMovementPhysicsState movementPhysicsState = new AgentMovementPhysicsState();

    public float verticalVelocity() {
        return movementPhysicsState.verticalVelocity();
    }

    public void setVerticalVelocity(float verticalVelocity) {
        movementPhysicsState.setVerticalVelocity(verticalVelocity);
    }

    private final AgentAirborneSteeringState airborneSteeringState = new AgentAirborneSteeringState();
    private final AgentDownJumpState downJumpState = new AgentDownJumpState();
    private final AgentMovementInputState movementInputState = new AgentMovementInputState();
    private final AgentSwimIntentState swimIntentState = new AgentSwimIntentState();
    private final AgentClimbState climbState = new AgentClimbState();

    public boolean inAir() {
        return movementPhysicsState.inAir();
    }

    public void setInAir(boolean inAir) {
        movementPhysicsState.setInAir(inAir);
    }

    public double fallPeakPhysicsY() {
        return movementPhysicsState.fallPeakPhysicsY();
    }

    public void setFallPeakPhysicsY(double fallPeakPhysicsY) {
        movementPhysicsState.setFallPeakPhysicsY(fallPeakPhysicsY);
    }

    public void resetFallPeakPhysicsY() {
        movementPhysicsState.resetFallPeakPhysicsY();
    }

    public AgentGroundTravelState groundTravelState() {
        return movementPhysicsState.groundTravelState();
    }

    public double horizontalSpeed() {
        return movementPhysicsState.horizontalSpeed();
    }

    public void setHorizontalSpeed(double horizontalSpeed) {
        movementPhysicsState.setHorizontalSpeed(horizontalSpeed);
    }

    public double physicsX() {
        return movementPhysicsState.physicsX();
    }

    public double physicsY() {
        return movementPhysicsState.physicsY();
    }

    public void setPhysicsX(double physicsX) {
        movementPhysicsState.setPhysicsX(physicsX);
    }

    public void setPhysicsY(double physicsY) {
        movementPhysicsState.setPhysicsY(physicsY);
    }

    public void setPhysicsPosition(double physicsX, double physicsY) {
        movementPhysicsState.setPhysicsPosition(physicsX, physicsY);
    }

    public void addPhysicsPosition(double deltaX, double deltaY) {
        movementPhysicsState.addPhysicsPosition(deltaX, deltaY);
    }

    public void setPhysicsPosition(Point position) {
        if (position != null) {
            setPhysicsPosition(position.x, position.y);
        }
    }

    public double groundPhysicsCarryMs() {
        return movementPhysicsState.groundCarryMs();
    }

    public void setGroundPhysicsCarryMs(double groundPhysicsCarryMs) {
        movementPhysicsState.setGroundCarryMs(groundPhysicsCarryMs);
    }

    public int jumpCooldownMs() {
        return movementPhysicsState.jumpCooldownMs();
    }

    public void setJumpCooldownMs(int jumpCooldownMs) {
        movementPhysicsState.setJumpCooldownMs(jumpCooldownMs);
    }

    public AgentMovementPhysicsState movementPhysicsState() {
        return movementPhysicsState;
    }

    public int facingDirection() {
        return movementInputState.facingDirection();
    }

    public void setFacingDirection(int facingDirection) {
        movementInputState.setFacingDirection(facingDirection);
    }

    public void setScriptedMovementFrame(Point position,
                                         int velocityX,
                                         int velocityY,
                                         int facingDirection,
                                         boolean inAir,
                                         boolean climbing) {
        movementPhysicsState.setPhysicsPosition(position.x, position.y);
        movementInputState.setVelocity(velocityX, velocityY);
        setFacingDirection(facingDirection);
        movementPhysicsState.setInAir(inAir);
        climbState.setClimbingFlag(climbing);
    }

    public int movementVelX() {
        return movementInputState.velocityX();
    }

    public int movementVelY() {
        return movementInputState.velocityY();
    }

    public boolean hasMovementVelocity() {
        return movementInputState.hasVelocity();
    }

    public void setMovementVelocity(int velocityX, int velocityY) {
        movementInputState.setVelocity(velocityX, velocityY);
    }

    // Swim intent — set by movement layer, consumed by physics engine. Movement
    // expresses "what the bot is trying to do"; physics integrates accordingly.
    // Mirrors how the real client only exposes discrete inputs (steer L/R,
    // jump-burst, hold UP/DOWN) — no continuous velocity overrides.
    public AgentSwimIntentState swimIntentState() {
        return swimIntentState;
    }

    public boolean swimming() {
        return swimIntentState.swimming();
    }

    public void setSwimming(boolean swimming) {
        swimIntentState.setSwimming(swimming);
    }

    public int swimMoveDirection() {
        return swimIntentState.moveDirection();
    }

    public void setSwimMoveDirection(int direction) {
        swimIntentState.setMoveDirection(direction);
    }

    public int swimVerticalHold() {
        return swimIntentState.verticalHold();
    }

    public void setSwimVerticalHold(int verticalHold) {
        swimIntentState.setVerticalHold(verticalHold);
    }

    public boolean swimJumpRequested() {
        return swimIntentState.jumpRequested();
    }

    public void setSwimJumpRequested(boolean swimJumpRequested) {
        swimIntentState.setJumpRequested(swimJumpRequested);
    }

    public long swimNextJumpAtMs() {
        return swimIntentState.nextJumpAtMs();
    }

    public void setSwimNextJumpAtMs(long swimNextJumpAtMs) {
        swimIntentState.setNextJumpAtMs(swimNextJumpAtMs);
    }

    // Movement intent — set by movement/fidget layer, consumed by physics engine.
    // Maps to the same left/right key hold used by the real client for both
    // ground walking and air steering. Physics reads this in the active mode:
    //   - Ground: applyGroundMotion() integrates through force/friction model
    //   - Airborne: stepAirborne() applies air steering accel (gated by fixedAirArc)
    // Mutually exclusive by state (inAir vs grounded), so one field suffices.
    public AgentMovementInputState movementInputState() {
        return movementInputState;
    }

    public int moveDirection() {
        return movementInputState.moveDirection();
    }

    public void setMoveDirection(int moveDirection) {
        movementInputState.setMoveDirection(moveDirection);
    }

    public void clearMoveDirection() {
        movementInputState.clearMoveDirection();
    }

    // Rope climbing
    public AgentClimbState climbState() {
        return climbState;
    }

    public boolean climbing() {
        return climbState.climbing();
    }

    // Climb intent — set by movement layer, consumed by physics engine.
    public Rope climbRope() {
        return climbState.climbRope();
    }

    public void setClimbingOnRope(Rope rope) {
        climbState.setClimbingOnRope(rope);
    }

    public int climbVerticalDirection() {
        return climbState.verticalDirection();
    }

    public void setClimbVerticalDirection(int direction) {
        climbState.setVerticalDirection(direction);
    }

    // Horizontal movement hysteresis
    public boolean wasMovingX() {
        return movementInputState.wasMovingX();
    }

    public void setWasMovingX(boolean wasMovingX) {
        movementInputState.setWasMovingX(wasMovingX);
    }

    public AgentAirborneSteeringState airborneSteeringState() {
        return airborneSteeringState;
    }

    public int airVelocityX() {
        return airborneSteeringState.velocityX();
    }

    public void setAirVelocityX(int airVelocityX) {
        airborneSteeringState.setVelocityX(airVelocityX);
    }

    public double airSteerVelocityX() {
        return airborneSteeringState.steeringVelocityX();
    }

    public void setAirSteerVelocityX(double airSteerVelocityX) {
        airborneSteeringState.setSteeringVelocityX(airSteerVelocityX);
    }

    public boolean fixedAirArc() {
        return airborneSteeringState.fixedAirArc();
    }

    public void setFixedAirArc(boolean fixedAirArc) {
        airborneSteeringState.setFixedAirArc(fixedAirArc);
    }

    // Movement intent
    public boolean climbUpIntent() {
        return climbState.climbUpIntent();
    }

    public void setClimbUpIntent(boolean climbUpIntent) {
        climbState.setClimbUpIntent(climbUpIntent);
    }

    public Rope blockedRopeGrab() {
        return climbState.blockedRopeGrab();
    }

    public void setBlockedRopeGrab(Rope rope) {
        climbState.setBlockedRopeGrab(rope);
    }

    public void clearBlockedRopeGrab() {
        climbState.clearBlockedRopeGrab();
    }

    public int ropeGrabCooldownMs() {
        return climbState.ropeGrabCooldownMs();
    }

    public void setRopeGrabCooldownMs(int ropeGrabCooldownMs) {
        climbState.setRopeGrabCooldownMs(ropeGrabCooldownMs);
    }

    public AgentDownJumpState downJumpState() {
        return downJumpState;
    }

    public boolean downJumpPending() {
        return downJumpState.pending();
    }

    public long downJumpGracePeriodMs() {
        return downJumpState.gracePeriodMs();
    }

    public void setDownJumpPending(boolean downJumpPending) {
        downJumpState.setPending(downJumpPending);
    }

    public void setDownJumpGracePeriodMs(long downJumpGracePeriodMs) {
        downJumpState.setGracePeriodMs(downJumpGracePeriodMs);
    }

    public boolean ropeEntryPending() {
        return climbState.ropeEntryPending();
    }

    public Rope ropeEntryRope() {
        return climbState.ropeEntryRope();
    }

    public int ropeEntryY() {
        return climbState.ropeEntryY();
    }

    public void queueRopeEntry(Rope rope, int y) {
        climbState.queueRopeEntry(rope, y);
    }

    public void clearRopeEntry() {
        climbState.clearRopeEntry();
    }

    // Grind mode
    volatile boolean grinding = false;
    private final AgentCombatCooldownState combatCooldownState = new AgentCombatCooldownState();
    private final AgentGrindTargetState grindTargetState = new AgentGrindTargetState();

    public Monster grindTarget() {
        return grindTargetState.target();
    }

    public void setGrindTarget(Monster grindTarget) {
        grindTargetState.setTarget(grindTarget);
    }

    public void clearGrindTarget() {
        grindTargetState.clearTarget();
    }

    public long nextGrindTargetSearchAtMs() {
        return grindTargetState.nextSearchAtMs();
    }

    public void setNextGrindTargetSearchAtMs(long nextGrindTargetSearchAtMs) {
        grindTargetState.setNextSearchAtMs(nextGrindTargetSearchAtMs);
    }

    public void clearNextGrindTargetSearchAtMs() {
        grindTargetState.clearNextSearchAtMs();
    }

    public AgentGrindTargetState grindTargetState() {
        return grindTargetState;
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

    private final AgentCombatSkillCacheState combatSkillCacheState = new AgentCombatSkillCacheState();
    private final AgentCombatBuffState combatBuffState = new AgentCombatBuffState();

    public boolean supportHealsEnabled() {
        return combatBuffState.supportHealsEnabled();
    }

    public void setSupportHealsEnabled(boolean supportHealsEnabled) {
        combatBuffState.setSupportHealsEnabled(supportHealsEnabled);
    }

    public boolean skillBuffsEnabled() {
        return combatBuffState.skillBuffsEnabled();
    }

    public void setSkillBuffsEnabled(boolean skillBuffsEnabled) {
        combatBuffState.setSkillBuffsEnabled(skillBuffsEnabled);
    }

    public long nextBuffAt(int skillId) {
        return combatBuffState.nextBuffAt(skillId);
    }

    public void ensureNextBuffAt(int skillId, long nextAt) {
        combatBuffState.ensureNextBuffAt(skillId, nextAt);
    }

    public void setNextBuffAt(int skillId, long nextAt) {
        combatBuffState.setNextBuffAt(skillId, nextAt);
    }

    public long nextSupportBuffAt(int skillId) {
        return combatBuffState.nextSupportBuffAt(skillId);
    }

    public void setNextSupportBuffAt(int skillId, long nextAt) {
        combatBuffState.setNextSupportBuffAt(skillId, nextAt);
    }

    public AgentCombatBuffState combatBuffState() {
        return combatBuffState;
    }

    public boolean skillCacheMatches(int jobId, int level, int signature) {
        return combatSkillCacheState.matches(jobId, level, signature);
    }

    public void resetSkillCache(int jobId, int level, int signature) {
        combatSkillCacheState.reset(jobId, level, signature);
    }

    public List<Integer> attackSkillIds() {
        return combatSkillCacheState.attackSkillIds();
    }

    public void addAttackSkillId(int skillId) {
        combatSkillCacheState.addAttackSkillId(skillId);
    }

    public int attackSkillId() {
        return combatSkillCacheState.attackSkillId();
    }

    public void setAttackSkillId(int attackSkillId) {
        combatSkillCacheState.setAttackSkillId(attackSkillId);
    }

    public int aoeSkillId() {
        return combatSkillCacheState.aoeSkillId();
    }

    public int aoeSkillMobs() {
        return combatSkillCacheState.aoeSkillMobs();
    }

    public void setAoeSkill(int skillId, int mobCount) {
        combatSkillCacheState.setAoeSkill(skillId, mobCount);
    }

    public int healSkillId() {
        return combatSkillCacheState.healSkillId();
    }

    public void setHealSkillId(int healSkillId) {
        combatSkillCacheState.setHealSkillId(healSkillId);
    }

    public List<Integer> buffSkillIds() {
        return combatSkillCacheState.buffSkillIds();
    }

    public void addBuffSkillId(int skillId) {
        combatSkillCacheState.addBuffSkillId(skillId);
    }

    public List<Integer> summonSkillIds() {
        return combatSkillCacheState.summonSkillIds();
    }

    public void addSummonSkillId(int skillId) {
        combatSkillCacheState.addSummonSkillId(skillId);
    }

    public AgentCombatSkillCacheState combatSkillCacheState() {
        return combatSkillCacheState;
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
    private final AgentDegenerateAttackState degenerateAttackState = new AgentDegenerateAttackState();
    private final AgentRetreatHoldState retreatHoldState = new AgentRetreatHoldState();
    private final AgentBreakoutState breakoutState = new AgentBreakoutState();
    private final AgentAoeRepositionState aoeRepositionState = new AgentAoeRepositionState();
    private final AgentGrindWanderState grindWanderState = new AgentGrindWanderState();

    public AgentDegenerateAttackState degenerateAttackState() { return degenerateAttackState; }

    public boolean degenAttackDone() { return degenerateAttackState.done(); }

    public void markDegenAttackDone() {
        degenerateAttackState.markDone();
    }

    public void clearDegenAttackDone() {
        degenerateAttackState.clear();
    }

    public AgentRetreatHoldState retreatHoldState() { return retreatHoldState; }

    public Point retreatHoldPos() { return retreatHoldState.position(); }

    public long retreatHoldUntilMs() { return retreatHoldState.untilMs(); }

    public boolean hasRetreatHold() { return retreatHoldState.hasHold(); }

    public void setRetreatHold(Point position, long untilMs) {
        retreatHoldState.set(position, untilMs);
    }

    public void clearRetreatHold() {
        retreatHoldState.clear();
    }

    public AgentBreakoutState breakoutState() { return breakoutState; }

    public int breakoutDirection() { return breakoutState.direction(); }

    public long breakoutUntilMs() { return breakoutState.untilMs(); }

    public boolean hasBreakoutCommitment() { return breakoutState.hasCommitment(); }

    public void setBreakoutCommitment(int direction, long untilMs) {
        breakoutState.setCommitment(direction, untilMs);
    }

    public void clearBreakoutCommitment() {
        breakoutState.clear();
    }

    public AgentAoeRepositionState aoeRepositionState() { return aoeRepositionState; }

    public Point aoeRepositionAnchor() { return aoeRepositionState.anchor(); }

    public boolean hasAoeRepositionAnchor() { return aoeRepositionState.hasAnchor(); }

    public long aoeRepositionDeadlineMs() { return aoeRepositionState.deadlineMs(); }

    public void setAoeRepositionAnchor(Point anchor, long deadlineMs) {
        aoeRepositionState.setAnchor(anchor, deadlineMs);
    }

    public void clearAoeRepositionAnchor() {
        aoeRepositionState.clear();
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
    private final AgentMoveTargetState moveTargetState = new AgentMoveTargetState();

    public AgentMoveTargetState moveTargetState() { return moveTargetState; }
    public java.awt.Point moveTarget() { return moveTargetState.target(); }
    public boolean isMoveTargetPrecise() { return moveTargetState.precise(); }
    public boolean hasMoveTarget() { return moveTargetState.hasTarget(); }
    public void setMoveTarget(java.awt.Point moveTarget, boolean precise) {
        moveTargetState.setTarget(moveTarget, precise);
    }
    public void clearMoveTarget() {
        moveTargetState.clear();
    }
    public boolean moveTargetEquals(java.awt.Point point) {
        return moveTargetState.targetEquals(point);
    }
    public java.awt.Point farmAnchor() { return farmAnchorState.anchor(); }
    public int farmAnchorMapId() { return farmAnchorState.mapId(); }
    public boolean hasFarmAnchor() { return farmAnchorState.hasAnchor(); }
    public void setFarmAnchor(java.awt.Point farmAnchor, int mapId) {
        farmAnchorState.setAnchor(farmAnchor, mapId);
    }
    public void clearFarmAnchor() {
        farmAnchorState.clear();
    }
    public int patrolRegionId() { return patrolState.regionId(); }
    public int patrolMapId() { return patrolState.mapId(); }
    public java.awt.Point patrolWanderTarget() {
        return patrolState.wanderTarget();
    }
    public boolean hasPatrolRegion() { return patrolState.hasRegion(); }
    public void setPatrolRegion(int regionId, int mapId) {
        patrolState.setRegion(regionId, mapId);
    }
    public void clearPatrol() {
        patrolState.clear();
    }
    public void setPatrolWanderTarget(java.awt.Point patrolWanderTarget) {
        patrolState.setWanderTarget(patrolWanderTarget);
    }
    public void clearPatrolWanderTarget() {
        patrolState.clearWanderTarget();
    }
    public AgentGrindWanderState grindWanderState() { return grindWanderState; }
    public int wanderDirection() { return grindWanderState.direction(); }
    public void setWanderDirection(int wanderDirection) {
        grindWanderState.setDirection(wanderDirection);
    }
    public void clearWanderDirection() {
        grindWanderState.clear();
    }
    private final AgentGrindLootState grindLootState = new AgentGrindLootState();

    public AgentGrindLootState grindLootState() { return grindLootState; }
    public MapItem grindLootTarget() { return grindLootState.target(); }
    public boolean hasGrindLootTarget() { return grindLootState.hasTarget(); }
    public void setGrindLootTarget(MapItem grindLootTarget) {
        grindLootState.setTarget(grindLootTarget);
    }
    public void clearGrindLootTarget() {
        grindLootState.clearTarget();
    }
    public int ignoredGrindLootObjectId() { return grindLootState.ignoredObjectId(); }
    public long ignoredGrindLootUntilMs() { return grindLootState.ignoredUntilMs(); }
    public void suppressGrindLootRetry(int objectId, long untilMs) {
        grindLootState.suppressRetry(objectId, untilMs);
    }
    public void clearGrindLootRetrySuppression() {
        grindLootState.clearRetrySuppression();
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
    private final AgentReplyChannelState replyChannelState = new AgentReplyChannelState();

    public AgentReplyChannelState replyChannelState() {
        return replyChannelState;
    }

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

    private final AgentPendingTradeSequenceState pendingTradeSequenceState = new AgentPendingTradeSequenceState();

    public AgentPendingTradeSequenceState pendingTradeSequenceState() {
        return pendingTradeSequenceState;
    }

    public String pendingTradeCategory() {
        return pendingTradeSequenceState.category();
    }

    public void setPendingTradeCategory(String pendingTradeCategory) {
        pendingTradeSequenceState.setCategory(pendingTradeCategory);
    }

    public List<Item> pendingTradeItems() {
        return pendingTradeSequenceState.items();
    }

    public void setPendingTradeItems(List<Item> pendingTradeItems) {
        pendingTradeSequenceState.setItems(pendingTradeItems);
    }

    public int pendingPotShareBudget() {
        return pendingTradeSequenceState.shareBudget();
    }

    public void setPendingPotShareBudget(int pendingPotShareBudget) {
        pendingTradeSequenceState.setShareBudget(pendingPotShareBudget);
    }

    public String pendingTradeCategoryMsg() {
        return pendingTradeSequenceState.categoryMessage();
    }

    public void setPendingTradeCategoryMsg(String pendingTradeCategoryMsg) {
        pendingTradeSequenceState.setCategoryMessage(pendingTradeCategoryMsg);
    }

    public int pendingTradeRecipientId() {
        return pendingTradeSequenceState.recipientId();
    }

    public void setPendingTradeRecipientId(int pendingTradeRecipientId) {
        pendingTradeSequenceState.setRecipientId(pendingTradeRecipientId);
    }

    public boolean pendingTradeInviteAnnounced() {
        return pendingTradeSequenceState.inviteAnnounced();
    }

    public void setPendingTradeInviteAnnounced(boolean pendingTradeInviteAnnounced) {
        pendingTradeSequenceState.setInviteAnnounced(pendingTradeInviteAnnounced);
    }

    public int pendingTradeTimerMs() {
        return pendingTradeSequenceState.timerMs();
    }

    public void setPendingTradeTimerMs(int pendingTradeTimerMs) {
        pendingTradeSequenceState.setTimerMs(pendingTradeTimerMs);
    }

    public boolean pendingTradeSingleBatch() {
        return pendingTradeSequenceState.singleBatch();
    }

    public void setPendingTradeSingleBatch(boolean pendingTradeSingleBatch) {
        pendingTradeSequenceState.setSingleBatch(pendingTradeSingleBatch);
    }

    public int pendingTradeMeso() {
        return pendingTradeSequenceState.meso();
    }

    public void setPendingTradeMeso(int pendingTradeMeso) {
        pendingTradeSequenceState.setMeso(pendingTradeMeso);
    }

    public boolean pendingTradeMesoAdded() {
        return pendingTradeSequenceState.mesoAdded();
    }

    public void setPendingTradeMesoAdded(boolean pendingTradeMesoAdded) {
        pendingTradeSequenceState.setMesoAdded(pendingTradeMesoAdded);
    }

    public boolean pendingTradeAllAdded() {
        return pendingTradeSequenceState.allItemsAdded();
    }

    public void setPendingTradeAllAdded(boolean pendingTradeAllAdded) {
        pendingTradeSequenceState.setAllItemsAdded(pendingTradeAllAdded);
    }

    public boolean pendingTradeBotDone() {
        return pendingTradeSequenceState.agentDone();
    }

    public void setPendingTradeBotDone(boolean pendingTradeBotDone) {
        pendingTradeSequenceState.setAgentDone(pendingTradeBotDone);
    }

    public int pendingTradeIdx() {
        return pendingTradeSequenceState.itemIndex();
    }

    public void setPendingTradeIdx(int pendingTradeIdx) {
        pendingTradeSequenceState.setItemIndex(pendingTradeIdx);
    }

    public Map<Item, Short> pendingTradeRestoreSlots() {
        return pendingTradeSequenceState.restoreSlots();
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

    public int skipDelayMs() {
        return tickState.skipDelayMs();
    }

    public void setSkipDelayMs(int skipDelayMs) {
        tickState.setSkipDelayMs(skipDelayMs);
    }

    public int aiTickAccumulatorMs() {
        return tickState.aiTickAccumulatorMs();
    }

    public void setAiTickAccumulatorMs(int aiTickAccumulatorMs) {
        tickState.setAiTickAccumulatorMs(aiTickAccumulatorMs);
    }

    // "Move here" target — bot navigates to this fixed point, then idles until cleared
    // Explicit move-target storage now lives in AgentMoveTargetState.
    // "Farm here" anchor — bot returns to this fixed point and only takes local attacks.
    // "Patrol" region — bot wanders within this nav region and attacks opportunistically.

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

    private final AgentOwnerGivenTradeItemState ownerGivenTradeItemState = new AgentOwnerGivenTradeItemState();

    public AgentOwnerGivenTradeItemState ownerGivenTradeItemState() {
        return ownerGivenTradeItemState;
    }

    public Set<Item> ownerGivenItems() {
        return ownerGivenTradeItemState.items();
    }

    private final AgentNavigationDebugState navigationDebugState = new AgentNavigationDebugState();
    private final AgentNavigationEdgeState navigationEdgeState = new AgentNavigationEdgeState();
    private final AgentNavigationTargetState navigationTargetState = new AgentNavigationTargetState();
    private final AgentFarmAnchorState farmAnchorState = new AgentFarmAnchorState();
    private final AgentMovementBroadcastState movementBroadcastState = new AgentMovementBroadcastState();
    private final AgentMovementPhysicsCacheState movementPhysicsCacheState = new AgentMovementPhysicsCacheState();
    private final AgentMovementStuckState movementStuckState = new AgentMovementStuckState();
    private final AgentOwnerMotionState ownerMotionState = new AgentOwnerMotionState();
    private final AgentPatrolState patrolState = new AgentPatrolState();
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
    private final AgentFidgetState fidgetState = new AgentFidgetState();

    public AgentFidgetState fidgetState() {
        return fidgetState;
    }

    public AgentFidgetMode fidgetMode() {
        return fidgetState.mode();
    }

    public boolean hasActiveFidgetMode() {
        return fidgetState.active();
    }

    public AgentFidgetTrigger fidgetTrigger() {
        return fidgetState.trigger();
    }

    public long fidgetUntilMs() {
        return fidgetState.untilMs();
    }

    public long nextFidgetActionAtMs() {
        return fidgetState.nextActionAtMs();
    }

    public int fidgetAirSteerDir() {
        return fidgetState.airSteerDir();
    }

    public int fidgetJumpDir() {
        return fidgetState.jumpDir();
    }

    public int fidgetMoveDir() {
        return fidgetState.moveDir();
    }

    public boolean fidgetSpamAirSteer() {
        return fidgetState.spamAirSteer();
    }

    public int fidgetActionBaseDelayMs() {
        return fidgetState.actionBaseDelayMs();
    }

    public long nextFidgetJumpAtMs() {
        return fidgetState.nextJumpAtMs();
    }

    public Point fidgetOriginPos() {
        return fidgetState.originPos();
    }

    public long nextFidgetVisualAtMs() {
        return fidgetState.nextVisualAtMs();
    }

    public long nextFidgetAtMs() {
        return fidgetState.nextFidgetAtMs();
    }

    public long nextIdleFidgetRollAtMs() {
        return fidgetState.nextIdleRollAtMs();
    }

    public boolean crouching() {
        return movementInputState.crouching();
    }

    public void setCrouching(boolean crouching) {
        movementInputState.setCrouching(crouching);
    }

    public void clearFidgetState() {
        fidgetState.clearActiveState();
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
        fidgetState.start(
                mode,
                trigger,
                untilMs,
                nowMs,
                airSteerDir,
                spamAirSteer,
                actionBaseDelayMs,
                originPos,
                nextVisualAtMs,
                nextFidgetAtMs);
    }

    public void setNextIdleFidgetRollAtMs(long nextIdleFidgetRollAtMs) {
        fidgetState.setNextIdleRollAtMs(nextIdleFidgetRollAtMs);
    }

    public void setNextFidgetAtMs(long nextFidgetAtMs) {
        fidgetState.setNextFidgetAtMs(nextFidgetAtMs);
    }

    public void setFidgetAirSteerDir(int fidgetAirSteerDir) {
        fidgetState.setAirSteerDir(fidgetAirSteerDir);
    }

    public void setNextFidgetActionAtMs(long nextFidgetActionAtMs) {
        fidgetState.setNextActionAtMs(nextFidgetActionAtMs);
    }

    public void setFidgetJumpDir(int fidgetJumpDir) {
        fidgetState.setJumpDir(fidgetJumpDir);
    }

    public void setNextFidgetJumpAtMs(long nextFidgetJumpAtMs) {
        fidgetState.setNextJumpAtMs(nextFidgetJumpAtMs);
    }

    public void setFidgetMoveDir(int fidgetMoveDir) {
        fidgetState.setMoveDir(fidgetMoveDir);
    }

    public void setNextFidgetVisualAtMs(long nextFidgetVisualAtMs) {
        fidgetState.setNextVisualAtMs(nextFidgetVisualAtMs);
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

    public AgentFarmAnchorState farmAnchorState() {
        return farmAnchorState;
    }

    public AgentOwnerMotionState ownerMotionState() {
        return ownerMotionState;
    }

    public AgentPatrolState patrolState() {
        return patrolState;
    }

    public AgentMovementBroadcastState movementBroadcastState() {
        return movementBroadcastState;
    }

    public AgentMovementPhysicsCacheState movementPhysicsCacheState() {
        return movementPhysicsCacheState;
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

    public int lastGroundFhId() {
        return movementPhysicsCacheState.lastGroundFootholdId();
    }

    public void setLastGroundFhId(int lastGroundFhId) {
        movementPhysicsCacheState.setLastGroundFootholdId(lastGroundFhId);
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
    public AgentReplyChannel getReplyChannel() { return replyChannelState.channel(); }
    public void setReplyChannel(AgentReplyChannel replyChannel) { replyChannelState.setChannel(replyChannel); }
}
