package server.life.simulation;

import client.Character;
import server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig;
import server.life.Monster;
import server.maps.MapleMap;
import server.physics.FixedStepAccumulator;
import server.physics.MaplePhysicsConstants;
import server.physics.PhysicsBody;
import server.physics.PhysicsMode;
import server.physics.PhysicsStepResult;
import server.physics.PhysicsTerrain;
import server.physics.foothold.FootholdSegment;

import java.awt.Point;

/** Mutable per-monster state. It is stepped only by its channel service. */
public final class MobSimulationSession {
    private static final double PROGRESS_DISTANCE_PX = 1.0;
    private static final double GROUNDED_TOLERANCE_PX = 12.0;
    private static final double INITIAL_SUPPORT_MAX_DISTANCE_PX = 120.0;
    private static final double MOVEMENT_SNAPSHOT_POSITION_TOLERANCE_PX = 16.0;
    private static final long MOVEMENT_SNAPSHOT_MAX_AGE_NANOS = 500_000_000L;
    private static final long RANDOM_NONZERO_FALLBACK = 0x9E3779B97F4A7C15L;
    private final MapleMap map;
    private final Monster monster;
    private final MobPhysicsProfile profile;
    private final PhysicsTerrain terrain;
    private final PhysicsBody body;
    private final FixedStepAccumulator accumulator =
            new FixedStepAccumulator(MaplePhysicsConstants.STEP_MS);
    private static final MobPhysicsSimulator SIMULATOR = new MobPhysicsSimulator();
    private volatile Character agent;
    private volatile MobMotionState motion = MobMotionState.PENDING_IMPACT;
    private long generation;
    private long impactAtNanos;
    private long lastAcceptedHitNanos;
    private long lastTickNanos;
    private long tickNowNanos;
    private long lastPublishedNanos;
    private int lastPublishedMoveActivity = -1;
    private long lastPublishedMoveActivityNanos;
    private long nextJumpNanos;
    private int pendingDamage;
    private volatile int knockbackDirection = 1;
    private volatile boolean impactFacingLeft;
    private int knockbackStepsRemaining;
    private int recoveryStepsRemaining;
    private int chaseRampStepsTotal;
    private int chaseRampStepsRemaining;
    private double targetX;
    private double targetY;
    private boolean chasing = true;
    private boolean lastHitWall;
    private int blockedDirection;
    private boolean immediatePublication = true;
    private Point lastPublishedPosition;
    private long randomState;
    private double progressAnchorX;
    private long nextStuckDecisionNanos;
    private long temporaryBehaviorUntilNanos;
    private int temporaryDirection;
    private double temporaryRetreatStartX;
    private double temporaryRetreatDistancePx;
    private int chaseDirection;
    private int pendingChaseDirection;
    private long directionChangeAtNanos;
    private boolean edgeJumpOpportunity;
    private boolean platformConstrainedKnockback;
    private long serverCombatActionUntilNanos;
    private final boolean initialSupportCorrected;

    public MobSimulationSession(MapleMap map, Monster monster, Character agent,
                                MobPhysicsProfile profile, PhysicsTerrain terrain,
                                long nowNanos) {
        this.map = map;
        this.monster = monster;
        this.agent = agent;
        this.profile = profile;
        this.terrain = terrain;
        Point position = monster.getPosition();
        PhysicsMode mode = profile.mode();
        if (mode == PhysicsMode.NORMAL && map.isSwim()) {
            mode = PhysicsMode.SWIMMING;
        }
        body = new PhysicsBody(position.x, position.y, mode);
        randomState = mix64((((long) monster.getObjectId()) << 32)
                ^ Integer.toUnsignedLong(map.getId()) ^ RANDOM_NONZERO_FALLBACK);
        if (randomState == 0L) randomState = RANDOM_NONZERO_FALLBACK;
        MobMovementSnapshot movementSnapshot = usableMovementSnapshot(
                monster.getLastClientMovement(), position, nowNanos);
        int reportedFootholdId = movementSnapshot != null && movementSnapshot.footholdId() > 0
                ? movementSnapshot.footholdId() : monster.getFh();
        FootholdSegment reportedFoothold = terrain.foothold(reportedFootholdId);
        FootholdSegment foothold = validInitialSupport(reportedFoothold, position)
                ? reportedFoothold : boundedSupportBelow(terrain, position);
        initialSupportCorrected = reportedFoothold != foothold;
        if (foothold != null) {
            double ground = foothold.groundY(position.x);
            boolean grounded = mode == PhysicsMode.NORMAL
                    && (Math.abs(ground - position.y) <= GROUNDED_TOLERANCE_PX
                    || reportsGroundedSupport(movementSnapshot, foothold));
            body.setFoothold(foothold.id(), foothold.slope(), foothold.layer());
            body.setGrounded(grounded);
            body.setGroundBelow(ground);
            if (grounded) {
                body.setPosition(position.x, ground);
            }
        }
        if (movementSnapshot != null) {
            body.setVelocity(movementSnapshot.velocityX(), body.grounded()
                    ? 0.0 : movementSnapshot.velocityY());
        }
        lastTickNanos = nowNanos;
        lastAcceptedHitNanos = nowNanos;
        progressAnchorX = body.x();
        nextStuckDecisionNanos = nowNanos + stuckWindowNanos();
        nextJumpNanos = nowNanos + randomMillis(
                Math.max(0, AgentMobPhysicsConfig.config().MOB_PHYSICS_JUMP_COOLDOWN_JITTER_MS)) * 1_000_000L;
        lastPublishedPosition = new Point((int) Math.round(body.x()), (int) Math.round(body.y()));
    }

    private static boolean validInitialSupport(FootholdSegment foothold, Point position) {
        if (foothold == null || foothold.wall() || !foothold.containsX(position.x)) return false;
        double verticalDistance = foothold.groundY(position.x) - position.y;
        return verticalDistance >= -GROUNDED_TOLERANCE_PX
                && verticalDistance <= INITIAL_SUPPORT_MAX_DISTANCE_PX;
    }

    private static MobMovementSnapshot usableMovementSnapshot(
            MobMovementSnapshot snapshot, Point position, long nowNanos) {
        if (snapshot == null || !snapshot.isFresh(nowNanos, MOVEMENT_SNAPSHOT_MAX_AGE_NANOS)
                || !Double.isFinite(snapshot.x()) || !Double.isFinite(snapshot.y())
                || !Double.isFinite(snapshot.velocityX())
                || !Double.isFinite(snapshot.velocityY())) {
            return null;
        }
        return Math.abs(snapshot.x() - position.x) <= MOVEMENT_SNAPSHOT_POSITION_TOLERANCE_PX
                && Math.abs(snapshot.y() - position.y) <= MOVEMENT_SNAPSHOT_POSITION_TOLERANCE_PX
                ? snapshot : null;
    }

    private static FootholdSegment boundedSupportBelow(PhysicsTerrain terrain, Point position) {
        FootholdSegment below = terrain.findBelow(position.x, position.y - 1.0);
        return validInitialSupport(below, position) ? below : null;
    }

    public synchronized long acceptHit(Character newAgent, int damage, long delayMs,
                                       int direction, long nowNanos) {
        boolean reactionAlreadyInProgress = reactionInProgress();
        agent = newAgent;
        // A hit received during knockback/flinch cannot stack another reaction, but it still
        // renews the Agent aggro lease and may update which Agent is being chased afterwards.
        lastAcceptedHitNanos = Math.max(lastAcceptedHitNanos, nowNanos);
        generation++;
        if (reactionAlreadyInProgress) {
            return generation;
        }
        pendingDamage = Math.max(0, damage);
        knockbackDirection = Integer.compare(direction, 0);
        impactFacingLeft = (monster.getStance() & 1) != 0;
        long scaledDelayMs = Math.max(0L, delayMs)
                * Math.max(0, AgentMobPhysicsConfig.config().MOB_PHYSICS_IMPACT_DELAY_PERCENT) / 100L;
        scaledDelayMs = Math.max(0L, scaledDelayMs
                + AgentMobPhysicsConfig.config().MOB_PHYSICS_IMPACT_DELAY_OFFSET_MS);
        impactAtNanos = nowNanos + scaledDelayMs * 1_000_000L;
        motion = MobMotionState.PENDING_IMPACT;
        knockbackStepsRemaining = 0;
        recoveryStepsRemaining = 0;
        chaseRampStepsTotal = 0;
        chaseRampStepsRemaining = 0;
        temporaryBehaviorUntilNanos = 0L;
        temporaryDirection = 0;
        temporaryRetreatDistancePx = 0.0;
        immediatePublication = true;
        return generation;
    }

    public synchronized boolean reactionInProgress() {
        return generation > 0 && (motion == MobMotionState.PENDING_IMPACT
                || motion == MobMotionState.KNOCKBACK
                || motion == MobMotionState.FLINCH);
    }

    public synchronized void beginServerCombatAction(long untilNanos) {
        serverCombatActionUntilNanos = Math.max(serverCombatActionUntilNanos, untilNanos);
        if (!reactionInProgress()) {
            motion = MobMotionState.IDLE;
            chasing = false;
            if (body.grounded() && !profile.flying()) {
                body.setVelocity(0.0, 0.0);
            }
        }
        immediatePublication = true;
    }

    public synchronized boolean serverCombatActionActive(long nowNanos) {
        return nowNanos < serverCombatActionUntilNanos && !reactionInProgress();
    }

    public synchronized long generation() {
        return generation;
    }

    public synchronized boolean hasGeneration(long expectedGeneration) {
        return generation == expectedGeneration;
    }

    public synchronized boolean agentHitLeaseExpired(long nowNanos, long timeoutMs) {
        return timeoutMs > 0
                && nowNanos - lastAcceptedHitNanos >= timeoutMs * 1_000_000L;
    }

    public synchronized boolean agentHitLeaseExpiredNanos(long nowNanos, long timeoutNanos) {
        return timeoutNanos > 0 && nowNanos - lastAcceptedHitNanos >= timeoutNanos;
    }

    public synchronized AdvanceResult advance(long nowNanos) {
        return advance(nowNanos, MobPhysicsTuningSnapshot.capture());
    }

    public synchronized AdvanceResult advance(long nowNanos, MobPhysicsTuningSnapshot tuning) {
        tickNowNanos = nowNanos;
        Point target = agent.getPosition();
        if (target != null) {
            targetX = target.x;
            targetY = target.y;
        }
        long elapsed = Math.max(0L, nowNanos - lastTickNanos);
        lastTickNanos = nowNanos;
        FixedStepAccumulator.StepBatch batch = accumulator.accumulate(
                elapsed, tuning.maxCatchUpSteps());
        int recoveries = 0;
        int edgeClamps = 0;
        int unexpectedGroundLosses = 0;
        boolean changed = false;
        for (int i = 0; i < batch.steps(); i++) {
            long stepTime = nowNanos - batch.leftoverNanos()
                    - (long) (batch.steps() - i - 1) * MaplePhysicsConstants.STEP_MS * 1_000_000L;
            beginImpactIfDue(stepTime, tuning);
            double oldX = body.x();
            double oldY = body.y();
            boolean constrainedBeforeStep = platformConstrainedKnockback;
            PhysicsStepResult step = SIMULATOR.step(this, tuning);
            changed |= oldX != body.x() || oldY != body.y();
            recoveries += step.recovered() ? 1 : 0;
            if (constrainedBeforeStep && step.reachedEdge()) edgeClamps++;
            if (constrainedBeforeStep && step.leftGround()) unexpectedGroundLosses++;
        }
        return new AdvanceResult(batch.steps(), batch.capped(), recoveries, changed,
                edgeClamps, unexpectedGroundLosses);
    }

    private void beginImpactIfDue(long stepTimeNanos, MobPhysicsTuningSnapshot tuning) {
        if (motion != MobMotionState.PENDING_IMPACT || stepTimeNanos < impactAtNanos) {
            return;
        }
        if (pendingDamage >= profile.pushed() && profile.mode() != PhysicsMode.FIXED) {
            platformConstrainedKnockback = body.grounded() && !profile.flying();
            if (platformConstrainedKnockback) {
                SIMULATOR.beginGroundedKnockbackSupport(this);
                body.setVelocity(0.0, 0.0);
            } else if (!profile.flying()) {
                body.setVelocity(body.velocityX() + knockbackDirection
                        * MobPhysicsSimulator.AIR_KNOCKBACK_FORCE
                        * tuning.knockbackMultiplier(), body.velocityY());
            }
            motion = MobMotionState.KNOCKBACK;
            knockbackStepsRemaining = MobPhysicsSimulator.KNOCKBACK_STEPS;
        } else {
            motion = MobMotionState.CHASE;
        }
        progressAnchorX = body.x();
        nextStuckDecisionNanos = stepTimeNanos + stuckWindowNanos();
        immediatePublication = true;
    }

    private static boolean reportsGroundedSupport(
            MobMovementSnapshot snapshot, FootholdSegment foothold) {
        if (snapshot == null || foothold == null || snapshot.footholdId() != foothold.id()) {
            return false;
        }
        int stance = snapshot.stance() & 0xff;
        return stance == 0 || stance == 1 || stance == 4 || stance == 5;
    }

    void afterStep(PhysicsStepResult result) {
        lastHitWall = result.hitWall();
        if (motion == MobMotionState.KNOCKBACK
                && ((!platformConstrainedKnockback && result.landed())
                || --knockbackStepsRemaining <= 0)) {
            finishKnockback();
        } else if (motion == MobMotionState.FLINCH && --recoveryStepsRemaining <= 0) {
            motion = MobMotionState.CHASE;
            beginPostFlinchChaseRamp();
            immediatePublication = true;
        } else if (motion == MobMotionState.JUMPING && result.landed()) {
            motion = MobMotionState.CHASE;
            immediatePublication = true;
        }
        if (result.landed() || result.hitWall() || result.reachedEdge()) {
            immediatePublication = true;
        }
        if (result.hitWall() || result.reachedEdge()) {
            blockedDirection = Double.compare(targetX - body.x(), 0.0);
            chasing = false;
        }
        if (result.reachedEdge()) {
            edgeJumpOpportunity = true;
            beginTemporaryBehavior(
                    AgentMobPhysicsConfig.config().MOB_PHYSICS_EDGE_RETREAT_CHANCE_PERCENT,
                    AgentMobPhysicsConfig.config().MOB_PHYSICS_EDGE_IDLE_MIN_MS,
                    AgentMobPhysicsConfig.config().MOB_PHYSICS_EDGE_IDLE_MAX_MS,
                    AgentMobPhysicsConfig.config().MOB_PHYSICS_EDGE_RETREAT_MIN_MS,
                    AgentMobPhysicsConfig.config().MOB_PHYSICS_EDGE_RETREAT_MAX_MS);
        }
    }

    private void finishKnockback() {
        if (platformConstrainedKnockback) {
            SIMULATOR.endGroundedKnockbackSupport(this);
        }
        if (body.grounded() && !profile.flying()) {
            body.setVelocity(0.0, 0.0);
        }
        platformConstrainedKnockback = false;
        int recoveryMs = Math.max(0, AgentMobPhysicsConfig.config().MOB_PHYSICS_FLINCH_RECOVERY_MS);
        recoveryStepsRemaining = (recoveryMs + MaplePhysicsConstants.STEP_MS - 1)
                / MaplePhysicsConstants.STEP_MS;
        motion = recoveryStepsRemaining > 0 ? MobMotionState.FLINCH : MobMotionState.CHASE;
        immediatePublication = true;
    }

    boolean shouldJump(double dx, double dy, MobPhysicsTuningSnapshot tuning) {
        if (!profile.canJump() || !body.grounded() || hasTemporaryBehavior()
                || tickNowNanos < nextJumpNanos) {
            return false;
        }
        int height = tuning.jumpTargetHeight();
        if (!edgeJumpOpportunity && !lastHitWall && blockedDirection == 0 && dy >= -height) {
            return false;
        }
        double forward = Math.copySign(Math.max(8, tuning.maxSafeEdgePx()),
                dx == 0.0 ? 1.0 : dx);
        FootholdSegment landing = terrain.findBelow(body.x() + forward, body.y() - height);
        return landing != null && !landing.wall()
                && landing.groundY(body.x() + forward) - body.y() < 180.0;
    }

    void markJump() {
        long cooldownMs = Math.max(0, AgentMobPhysicsConfig.config().MOB_PHYSICS_JUMP_COOLDOWN_MS);
        cooldownMs += randomMillis(Math.max(0,
                AgentMobPhysicsConfig.config().MOB_PHYSICS_JUMP_COOLDOWN_JITTER_MS));
        nextJumpNanos = tickNowNanos + cooldownMs * 1_000_000L;
        motion = MobMotionState.JUMPING;
        blockedDirection = 0;
        edgeJumpOpportunity = false;
        immediatePublication = true;
    }

    public synchronized boolean publicationDue(long nowNanos) {
        return publicationDue(nowNanos, MobPhysicsTuningSnapshot.capture().publicationIntervalNanos());
    }

    public synchronized boolean publicationDue(long nowNanos, long intervalNanos) {
        return immediatePublication || nowNanos - lastPublishedNanos >= intervalNanos;
    }

    public synchronized Point markPublished(long nowNanos, Point current) {
        Point start = lastPublishedPosition;
        lastPublishedPosition = new Point(current);
        lastPublishedNanos = nowNanos;
        immediatePublication = false;
        return start;
    }

    /**
     * Requests a client move action when movement starts, its facing changes, or the current WZ
     * move cycle expires. Repeating it on every position packet restarts slow animations at frame zero.
     */
    public synchronized int rawActivityForPublication(int stance, long nowNanos) {
        boolean moveAnimationActive = motion == MobMotionState.CHASE
                || motion == MobMotionState.KNOCKBACK
                || motion == MobMotionState.FLINCH;
        if (!moveAnimationActive) {
            lastPublishedMoveActivity = -1;
            lastPublishedMoveActivityNanos = 0L;
            return -1;
        }
        int moveActivity = stance & 1;
        String animation = profile.flying() ? "fly" : "move";
        int configuredCycleMs = monster.getAnimationTime(animation);
        long cycleNanos = (configuredCycleMs > 0 ? configuredCycleMs : 500L) * 1_000_000L;
        if (lastPublishedMoveActivity == moveActivity
                && nowNanos - lastPublishedMoveActivityNanos < cycleNanos) {
            return -1;
        }
        lastPublishedMoveActivity = moveActivity;
        lastPublishedMoveActivityNanos = nowNanos;
        return moveActivity;
    }

    public synchronized MobPhysicsState snapshot() {
        return new MobPhysicsState(monster.getObjectId(), agent.getId(), motion,
                body.x(), body.y(), body.velocityX(), body.velocityY(), body.footholdId(), generation);
    }

    public MapleMap map() { return map; }
    public Monster monster() { return monster; }
    public Character agent() { return agent; }
    public MobPhysicsProfile profile() { return profile; }
    public PhysicsTerrain terrain() { return terrain; }
    public PhysicsBody body() { return body; }
    public MobMotionState motion() { return motion; }
    public void setMotion(MobMotionState motion) { this.motion = motion; }
    public int knockbackDirection() { return knockbackDirection; }
    public boolean impactFacingLeft() { return impactFacingLeft; }
    public boolean platformConstrainedKnockback() { return platformConstrainedKnockback; }
    public boolean initialSupportCorrected() { return initialSupportCorrected; }
    public double targetX() { return targetX; }
    public double targetY() { return targetY; }
    double consumeChaseRampMultiplier() {
        if (chaseRampStepsRemaining <= 0 || chaseRampStepsTotal <= 0) {
            return 1.0;
        }
        int step = chaseRampStepsTotal - chaseRampStepsRemaining + 1;
        chaseRampStepsRemaining--;
        return step / (double) chaseRampStepsTotal;
    }
    public boolean chasing() { return chasing; }
    public long tickNowNanos() { return tickNowNanos; }
    public void setChasing(boolean chasing) { this.chasing = chasing; }
    public boolean blockedAhead(double dx) {
        int direction = Double.compare(dx, 0.0);
        if (blockedDirection != 0 && direction != blockedDirection) blockedDirection = 0;
        return blockedDirection != 0;
    }

    void prepareGroundBehavior(double dx, MobPhysicsTuningSnapshot tuning) {
        if (hasTemporaryBehavior() && temporaryDirection != 0
                && Math.abs(body.x() - temporaryRetreatStartX)
                >= temporaryRetreatDistancePx) {
            temporaryBehaviorUntilNanos = tickNowNanos;
        }
        if (Math.abs(body.x() - progressAnchorX) >= PROGRESS_DISTANCE_PX) {
            progressAnchorX = body.x();
            nextStuckDecisionNanos = tickNowNanos + stuckWindowNanos();
        }
        if (temporaryBehaviorUntilNanos != 0L
                && tickNowNanos >= temporaryBehaviorUntilNanos) {
            temporaryBehaviorUntilNanos = 0L;
            temporaryDirection = 0;
            temporaryRetreatDistancePx = 0.0;
            blockedDirection = 0;
            chasing = true;
            progressAnchorX = body.x();
            nextStuckDecisionNanos = tickNowNanos + stuckWindowNanos();
        }
        if (!hasTemporaryBehavior() && tickNowNanos >= nextStuckDecisionNanos
                && Math.abs(dx) > tuning.stopDistanceX()) {
            beginTemporaryBehavior(
                    AgentMobPhysicsConfig.config().MOB_PHYSICS_STUCK_RETREAT_CHANCE_PERCENT,
                    AgentMobPhysicsConfig.config().MOB_PHYSICS_EDGE_IDLE_MIN_MS,
                    AgentMobPhysicsConfig.config().MOB_PHYSICS_EDGE_IDLE_MAX_MS,
                    AgentMobPhysicsConfig.config().MOB_PHYSICS_EDGE_RETREAT_MIN_MS,
                    AgentMobPhysicsConfig.config().MOB_PHYSICS_EDGE_RETREAT_MAX_MS);
        }
    }

    private void beginPostFlinchChaseRamp() {
        int rampMs = Math.max(0,
                AgentMobPhysicsConfig.config().MOB_PHYSICS_POST_FLINCH_CHASE_RAMP_MS);
        chaseRampStepsTotal = (rampMs + MaplePhysicsConstants.STEP_MS - 1)
                / MaplePhysicsConstants.STEP_MS;
        chaseRampStepsRemaining = chaseRampStepsTotal;
    }

    boolean hasTemporaryBehavior() {
        return temporaryBehaviorUntilNanos > tickNowNanos;
    }

    int temporaryDirection() {
        return temporaryDirection;
    }

    double temporaryRetreatDistancePx() {
        return temporaryRetreatDistancePx;
    }

    int chaseDirection(double dx) {
        int desired = Double.compare(dx, 0.0);
        if (desired == 0) return chaseDirection;
        if (chaseDirection == 0) {
            chaseDirection = desired;
            return chaseDirection;
        }
        if (desired == chaseDirection) {
            pendingChaseDirection = 0;
            return chaseDirection;
        }
        if (pendingChaseDirection != desired) {
            pendingChaseDirection = desired;
            directionChangeAtNanos = tickNowNanos + randomMillis(Math.max(0,
                    AgentMobPhysicsConfig.config().MOB_PHYSICS_DIRECTION_REACTION_MAX_MS)) * 1_000_000L;
        }
        if (tickNowNanos >= directionChangeAtNanos) {
            chaseDirection = pendingChaseDirection;
            pendingChaseDirection = 0;
        }
        return chaseDirection;
    }

    private void beginTemporaryBehavior(int retreatChancePercent,
                                        int idleMinMs, int idleMaxMs,
                                        int retreatMinMs, int retreatMaxMs) {
        int awayFromBlock = blockedDirection == 0
                ? (nextUnit() < 0.5 ? -1 : 1) : -blockedDirection;
        boolean retreat = nextUnit() * 100.0
                < Math.min(100, Math.max(0, retreatChancePercent));
        temporaryDirection = retreat ? awayFromBlock : 0;
        temporaryRetreatStartX = body.x();
        temporaryRetreatDistancePx = retreat
                ? randomRangeInclusive(
                        AgentMobPhysicsConfig.config().MOB_PHYSICS_RETREAT_MIN_DISTANCE_PX,
                        AgentMobPhysicsConfig.config().MOB_PHYSICS_RETREAT_MAX_DISTANCE_PX)
                : 0.0;
        long durationMs = retreat
                ? randomRangeInclusive(retreatMinMs, retreatMaxMs)
                : randomRangeInclusive(idleMinMs, idleMaxMs);
        temporaryBehaviorUntilNanos = tickNowNanos + durationMs * 1_000_000L;
        nextStuckDecisionNanos = temporaryBehaviorUntilNanos + stuckWindowNanos();
        immediatePublication = true;
    }

    private long stuckWindowNanos() {
        long delayMs = Math.max(0, AgentMobPhysicsConfig.config().MOB_PHYSICS_STUCK_DETECT_MS);
        delayMs += randomMillis(Math.max(0,
                AgentMobPhysicsConfig.config().MOB_PHYSICS_BEHAVIOR_JITTER_MS));
        return delayMs * 1_000_000L;
    }

    private long randomRangeInclusive(int first, int second) {
        int min = Math.max(0, Math.min(first, second));
        int max = Math.max(min, Math.max(first, second));
        return min + randomMillis(max - min);
    }

    private long randomMillis(int inclusiveMaximum) {
        if (inclusiveMaximum <= 0) return 0L;
        return (long) Math.floor(nextUnit() * (inclusiveMaximum + 1.0));
    }

    private double nextUnit() {
        long x = randomState;
        x ^= x >>> 12;
        x ^= x << 25;
        x ^= x >>> 27;
        randomState = x;
        long value = x * 0x2545F4914F6CDD1DL;
        return (value >>> 11) * 0x1.0p-53;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    public record AdvanceResult(int substeps, boolean catchUpCapped,
                                int invalidRecoveries, boolean positionChanged,
                                int edgeClamps, int unexpectedGroundLosses) {
    }
}
