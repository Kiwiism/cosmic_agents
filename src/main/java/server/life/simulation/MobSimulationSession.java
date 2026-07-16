package server.life.simulation;

import client.Character;
import server.agents.capabilities.combat.AgentCombatConfig;
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
    private static final long RANDOM_NONZERO_FALLBACK = 0x9E3779B97F4A7C15L;
    private final MapleMap map;
    private final Monster monster;
    private final MobPhysicsProfile profile;
    private final PhysicsTerrain terrain;
    private final PhysicsBody body;
    private final FixedStepAccumulator accumulator =
            new FixedStepAccumulator(MaplePhysicsConstants.STEP_MS);
    private final MobPhysicsSimulator simulator = new MobPhysicsSimulator();
    private volatile Character agent;
    private volatile MobMotionState motion = MobMotionState.PENDING_IMPACT;
    private long generation;
    private long impactAtNanos;
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
    private boolean hit1ActivityPending;
    private boolean hit1ActivityPublished;
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
        FootholdSegment foothold = terrain.foothold(monster.getFh());
        if (foothold == null) {
            foothold = terrain.findBelow(position.x, position.y - 1.0);
        }
        if (foothold != null) {
            double ground = foothold.groundY(position.x);
            boolean grounded = mode == PhysicsMode.NORMAL && Math.abs(ground - position.y) <= 12.0;
            body.setFoothold(foothold.id(), foothold.slope(), foothold.layer());
            body.setGrounded(grounded);
            body.setGroundBelow(ground);
            if (grounded) {
                body.setPosition(position.x, ground);
            }
        }
        lastTickNanos = nowNanos;
        progressAnchorX = body.x();
        nextStuckDecisionNanos = nowNanos + stuckWindowNanos();
        nextJumpNanos = nowNanos + randomMillis(
                Math.max(0, AgentCombatConfig.cfg.MOB_PHYSICS_JUMP_COOLDOWN_JITTER_MS)) * 1_000_000L;
        lastPublishedPosition = new Point((int) Math.round(body.x()), (int) Math.round(body.y()));
    }

    public synchronized long acceptHit(Character newAgent, int damage, long delayMs,
                                       int direction, long nowNanos) {
        agent = newAgent;
        pendingDamage = Math.max(0, damage);
        knockbackDirection = direction < 0 ? -1 : 1;
        impactFacingLeft = (monster.getStance() & 1) != 0;
        long scaledDelayMs = Math.max(0L, delayMs)
                * Math.max(0, AgentCombatConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_PERCENT) / 100L;
        scaledDelayMs = Math.max(0L, scaledDelayMs
                + AgentCombatConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_OFFSET_MS);
        impactAtNanos = nowNanos + scaledDelayMs * 1_000_000L;
        generation++;
        motion = MobMotionState.PENDING_IMPACT;
        knockbackStepsRemaining = 0;
        recoveryStepsRemaining = 0;
        hit1ActivityPending = false;
        hit1ActivityPublished = false;
        chaseRampStepsTotal = 0;
        chaseRampStepsRemaining = 0;
        temporaryBehaviorUntilNanos = 0L;
        temporaryDirection = 0;
        temporaryRetreatDistancePx = 0.0;
        immediatePublication = true;
        return generation;
    }

    public synchronized AdvanceResult advance(long nowNanos) {
        tickNowNanos = nowNanos;
        Point target = agent.getPosition();
        if (target != null) {
            targetX = target.x;
            targetY = target.y;
        }
        long elapsed = Math.max(0L, nowNanos - lastTickNanos);
        lastTickNanos = nowNanos;
        FixedStepAccumulator.StepBatch batch = accumulator.accumulate(elapsed,
                Math.max(1, AgentCombatConfig.cfg.MOB_PHYSICS_MAX_CATCH_UP_STEPS));
        int recoveries = 0;
        boolean changed = false;
        for (int i = 0; i < batch.steps(); i++) {
            long stepTime = nowNanos - batch.leftoverNanos()
                    - (long) (batch.steps() - i - 1) * MaplePhysicsConstants.STEP_MS * 1_000_000L;
            beginImpactIfDue(stepTime);
            double oldX = body.x();
            double oldY = body.y();
            PhysicsStepResult step = simulator.step(this);
            changed |= oldX != body.x() || oldY != body.y();
            recoveries += step.recovered() ? 1 : 0;
        }
        return new AdvanceResult(batch.steps(), batch.capped(), recoveries, changed);
    }

    private void beginImpactIfDue(long stepTimeNanos) {
        if (motion != MobMotionState.PENDING_IMPACT || stepTimeNanos < impactAtNanos) {
            return;
        }
        if (pendingDamage >= profile.pushed() && profile.mode() != PhysicsMode.FIXED) {
            body.setVelocity(0.0, body.grounded() || profile.flying()
                    ? 0.0 : body.velocityY());
            motion = MobMotionState.KNOCKBACK;
            knockbackStepsRemaining = MobPhysicsSimulator.KNOCKBACK_STEPS;
        } else {
            motion = MobMotionState.CHASE;
        }
        progressAnchorX = body.x();
        nextStuckDecisionNanos = stepTimeNanos + stuckWindowNanos();
        immediatePublication = true;
    }

    void afterStep(PhysicsStepResult result) {
        lastHitWall = result.hitWall();
        if (motion == MobMotionState.KNOCKBACK && --knockbackStepsRemaining <= 0) {
            body.setVelocity(0.0, body.grounded() || profile.flying()
                    ? 0.0 : body.velocityY());
            int recoveryMs = Math.max(0,
                    AgentCombatConfig.cfg.MOB_PHYSICS_FLINCH_RECOVERY_MS);
            recoveryStepsRemaining = (recoveryMs + MaplePhysicsConstants.STEP_MS - 1)
                    / MaplePhysicsConstants.STEP_MS;
            if (recoveryStepsRemaining > 0) {
                motion = MobMotionState.FLINCH;
                hit1ActivityPending = AgentCombatConfig.cfg.MOB_PHYSICS_HIT1_ENABLED;
                hit1ActivityPublished = false;
            } else {
                motion = MobMotionState.CHASE;
            }
            immediatePublication = true;
        } else if (motion == MobMotionState.FLINCH && --recoveryStepsRemaining <= 0) {
            motion = MobMotionState.CHASE;
            beginPostFlinchChaseRamp();
            if (hit1ActivityPublished) {
                lastPublishedMoveActivity = -1;
                lastPublishedMoveActivityNanos = 0L;
            }
            hit1ActivityPending = false;
            hit1ActivityPublished = false;
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
                    AgentCombatConfig.cfg.MOB_PHYSICS_EDGE_RETREAT_CHANCE_PERCENT,
                    AgentCombatConfig.cfg.MOB_PHYSICS_EDGE_IDLE_MIN_MS,
                    AgentCombatConfig.cfg.MOB_PHYSICS_EDGE_IDLE_MAX_MS,
                    AgentCombatConfig.cfg.MOB_PHYSICS_EDGE_RETREAT_MIN_MS,
                    AgentCombatConfig.cfg.MOB_PHYSICS_EDGE_RETREAT_MAX_MS);
        }
    }

    boolean shouldJump(double dx, double dy) {
        if (!profile.canJump() || !body.grounded() || hasTemporaryBehavior()
                || tickNowNanos < nextJumpNanos) {
            return false;
        }
        int height = Math.max(1, AgentCombatConfig.cfg.MOB_PHYSICS_JUMP_TARGET_HEIGHT);
        if (!edgeJumpOpportunity && !lastHitWall && blockedDirection == 0 && dy >= -height) {
            return false;
        }
        double forward = Math.copySign(Math.max(8,
                AgentCombatConfig.cfg.MOB_PHYSICS_MAX_SAFE_EDGE_PX), dx == 0.0 ? 1.0 : dx);
        FootholdSegment landing = terrain.findBelow(body.x() + forward, body.y() - height);
        return landing != null && !landing.wall()
                && landing.groundY(body.x() + forward) - body.y() < 180.0;
    }

    void markJump() {
        long cooldownMs = Math.max(0, AgentCombatConfig.cfg.MOB_PHYSICS_JUMP_COOLDOWN_MS);
        cooldownMs += randomMillis(Math.max(0,
                AgentCombatConfig.cfg.MOB_PHYSICS_JUMP_COOLDOWN_JITTER_MS));
        nextJumpNanos = tickNowNanos + cooldownMs * 1_000_000L;
        motion = MobMotionState.JUMPING;
        blockedDirection = 0;
        edgeJumpOpportunity = false;
        immediatePublication = true;
    }

    public synchronized boolean publicationDue(long nowNanos) {
        long interval = Math.max(20, AgentCombatConfig.cfg.MOB_PHYSICS_PUBLICATION_INTERVAL_MS)
                * 1_000_000L;
        return immediatePublication || nowNanos - lastPublishedNanos >= interval;
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
        if (motion == MobMotionState.FLINCH && hit1ActivityPending) {
            hit1ActivityPending = false;
            if (AgentCombatConfig.cfg.MOB_PHYSICS_HIT1_ENABLED) {
                hit1ActivityPublished = true;
                return impactFacingLeft ? 9 : 8;
            }
        }
        if (motion == MobMotionState.FLINCH && hit1ActivityPublished) {
            return -1;
        }
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
    public double targetX() { return targetX; }
    public double targetY() { return targetY; }
    public double speedMultiplier() {
        return Math.max(0, AgentCombatConfig.cfg.MOB_PHYSICS_SPEED_PERCENT) / 100.0;
    }
    public double knockbackMultiplier() {
        return Math.max(0, AgentCombatConfig.cfg.MOB_PHYSICS_KNOCKBACK_PERCENT) / 100.0;
    }
    double consumeChaseRampMultiplier() {
        if (chaseRampStepsRemaining <= 0 || chaseRampStepsTotal <= 0) {
            return 1.0;
        }
        int step = chaseRampStepsTotal - chaseRampStepsRemaining + 1;
        chaseRampStepsRemaining--;
        return step / (double) chaseRampStepsTotal;
    }
    public boolean chasing() { return chasing; }
    public void setChasing(boolean chasing) { this.chasing = chasing; }
    public boolean blockedAhead(double dx) {
        int direction = Double.compare(dx, 0.0);
        if (blockedDirection != 0 && direction != blockedDirection) blockedDirection = 0;
        return blockedDirection != 0;
    }

    void prepareGroundBehavior(double dx) {
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
                && Math.abs(dx) > Math.max(0, AgentCombatConfig.cfg.MOB_PHYSICS_STOP_DISTANCE_X)) {
            beginTemporaryBehavior(
                    AgentCombatConfig.cfg.MOB_PHYSICS_STUCK_RETREAT_CHANCE_PERCENT,
                    AgentCombatConfig.cfg.MOB_PHYSICS_EDGE_IDLE_MIN_MS,
                    AgentCombatConfig.cfg.MOB_PHYSICS_EDGE_IDLE_MAX_MS,
                    AgentCombatConfig.cfg.MOB_PHYSICS_EDGE_RETREAT_MIN_MS,
                    AgentCombatConfig.cfg.MOB_PHYSICS_EDGE_RETREAT_MAX_MS);
        }
    }

    private void beginPostFlinchChaseRamp() {
        int rampMs = Math.max(0,
                AgentCombatConfig.cfg.MOB_PHYSICS_POST_FLINCH_CHASE_RAMP_MS);
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
                    AgentCombatConfig.cfg.MOB_PHYSICS_DIRECTION_REACTION_MAX_MS)) * 1_000_000L;
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
                        AgentCombatConfig.cfg.MOB_PHYSICS_RETREAT_MIN_DISTANCE_PX,
                        AgentCombatConfig.cfg.MOB_PHYSICS_RETREAT_MAX_DISTANCE_PX)
                : 0.0;
        long durationMs = retreat
                ? randomRangeInclusive(retreatMinMs, retreatMaxMs)
                : randomRangeInclusive(idleMinMs, idleMaxMs);
        temporaryBehaviorUntilNanos = tickNowNanos + durationMs * 1_000_000L;
        nextStuckDecisionNanos = temporaryBehaviorUntilNanos + stuckWindowNanos();
        immediatePublication = true;
    }

    private long stuckWindowNanos() {
        long delayMs = Math.max(0, AgentCombatConfig.cfg.MOB_PHYSICS_STUCK_DETECT_MS);
        delayMs += randomMillis(Math.max(0,
                AgentCombatConfig.cfg.MOB_PHYSICS_BEHAVIOR_JITTER_MS));
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
                                int invalidRecoveries, boolean positionChanged) {
    }
}
