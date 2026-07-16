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
    private long nextJumpNanos;
    private int pendingDamage;
    private volatile int knockbackDirection = 1;
    private int flinchStepsRemaining;
    private double targetX;
    private double targetY;
    private boolean chasing = true;
    private boolean lastHitWall;
    private int blockedDirection;
    private boolean immediatePublication = true;
    private Point lastPublishedPosition;

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
        lastPublishedPosition = new Point((int) Math.round(body.x()), (int) Math.round(body.y()));
    }

    public synchronized long acceptHit(Character newAgent, int damage, long delayMs,
                                       int direction, long nowNanos) {
        agent = newAgent;
        pendingDamage = Math.max(0, damage);
        knockbackDirection = direction < 0 ? -1 : 1;
        impactAtNanos = nowNanos + Math.max(0L, delayMs) * 1_000_000L;
        generation++;
        motion = MobMotionState.PENDING_IMPACT;
        flinchStepsRemaining = 0;
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
            motion = MobMotionState.FLINCH;
            flinchStepsRemaining = MobPhysicsSimulator.FLINCH_STEPS;
        } else {
            motion = MobMotionState.CHASE;
        }
        immediatePublication = true;
    }

    void afterStep(PhysicsStepResult result) {
        lastHitWall = result.hitWall();
        if (motion == MobMotionState.FLINCH && --flinchStepsRemaining <= 0) {
            motion = MobMotionState.CHASE;
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
    }

    boolean shouldJump(double dx, double dy) {
        if (!profile.canJump() || !body.grounded() || tickNowNanos < nextJumpNanos) {
            return false;
        }
        int height = Math.max(1, AgentCombatConfig.cfg.MOB_PHYSICS_JUMP_TARGET_HEIGHT);
        if (!lastHitWall && blockedDirection == 0 && dy >= -height) {
            return false;
        }
        double forward = Math.copySign(Math.max(8,
                AgentCombatConfig.cfg.MOB_PHYSICS_MAX_SAFE_EDGE_PX), dx == 0.0 ? 1.0 : dx);
        FootholdSegment landing = terrain.findBelow(body.x() + forward, body.y() - height);
        return landing != null && !landing.wall()
                && landing.groundY(body.x() + forward) - body.y() < 180.0;
    }

    void markJump() {
        nextJumpNanos = tickNowNanos + Math.max(0,
                AgentCombatConfig.cfg.MOB_PHYSICS_JUMP_COOLDOWN_MS) * 1_000_000L;
        motion = MobMotionState.JUMPING;
        blockedDirection = 0;
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
    public double targetX() { return targetX; }
    public double targetY() { return targetY; }
    public boolean chasing() { return chasing; }
    public void setChasing(boolean chasing) { this.chasing = chasing; }
    public boolean blockedAhead(double dx) {
        int direction = Double.compare(dx, 0.0);
        if (blockedDirection != 0 && direction != blockedDirection) blockedDirection = 0;
        return blockedDirection != 0;
    }

    public record AdvanceResult(int substeps, boolean catchUpCapped,
                                int invalidRecoveries, boolean positionChanged) {
    }
}
