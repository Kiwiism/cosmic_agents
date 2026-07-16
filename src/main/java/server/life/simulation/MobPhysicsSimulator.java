package server.life.simulation;

import server.agents.capabilities.combat.AgentCombatConfig;
import server.physics.MaplePhysicsIntegrator;
import server.physics.PhysicsBody;
import server.physics.PhysicsInput;
import server.physics.PhysicsMode;
import server.physics.PhysicsStepResult;

/**
 * Monster policy adapter around the entity-independent fixed-step kernel.
 * Hit duration/forces and walk/fly behavior translate Mob.cpp from
 * https://github.com/nmnsnv/maplestory-wasm at
 * bc0234fe7c7f53322453e7bdd79564d9aca4cd8b (AGPL-3.0-or-later).
 */
public final class MobPhysicsSimulator {
    public static final int FLINCH_STEPS = 31;
    public static final double GROUND_KNOCKBACK_FORCE = 0.2;
    public static final double AIR_KNOCKBACK_FORCE = 0.1;
    public static final double JUMP_FORCE = -5.0;

    private final MaplePhysicsIntegrator integrator = new MaplePhysicsIntegrator();

    public PhysicsStepResult step(MobSimulationSession session) {
        PhysicsBody body = session.body();
        MobPhysicsProfile profile = session.profile();
        double horizontal = 0.0;
        double vertical = 0.0;
        boolean turnAtEdges = false;

        if (session.motion() == MobMotionState.FLINCH) {
            horizontal = session.knockbackDirection()
                    * (body.grounded() && !profile.flying()
                    ? GROUND_KNOCKBACK_FORCE : AIR_KNOCKBACK_FORCE);
        } else if (session.motion() != MobMotionState.PENDING_IMPACT
                && profile.mode() != PhysicsMode.FIXED) {
            double dx = session.targetX() - body.x();
            double dy = session.targetY() - body.y();
            if (profile.flying()) {
                horizontal = forceOutside(dx, AgentCombatConfig.cfg.MOB_PHYSICS_FLY_DEAD_ZONE_X,
                        profile.flyingForce());
                vertical = forceOutside(dy, AgentCombatConfig.cfg.MOB_PHYSICS_FLY_DEAD_ZONE_Y,
                        profile.flyingForce());
                session.setMotion(horizontal == 0.0 && vertical == 0.0
                        ? MobMotionState.IDLE : MobMotionState.CHASE);
            } else {
                updateGroundHysteresis(session, dx);
                if (session.chasing()) {
                    horizontal = Math.copySign(profile.walkingForce(), dx);
                    turnAtEdges = true;
                    session.setMotion(MobMotionState.CHASE);
                } else {
                    session.setMotion(MobMotionState.IDLE);
                }
                if (session.shouldJump(dx, dy)) {
                    vertical = JUMP_FORCE;
                    turnAtEdges = false;
                    session.markJump();
                }
            }
        }

        PhysicsStepResult result = integrator.step(body,
                new PhysicsInput(horizontal, vertical, turnAtEdges, false), session.terrain());
        session.afterStep(result);
        return result;
    }

    private static double forceOutside(double delta, int deadZone, double force) {
        return Math.abs(delta) <= Math.max(0, deadZone) ? 0.0 : Math.copySign(force, delta);
    }

    private static void updateGroundHysteresis(MobSimulationSession session, double delta) {
        double distance = Math.abs(delta);
        if (session.blockedAhead(delta)) {
            session.setChasing(false);
            return;
        }
        if (session.chasing()) {
            if (distance <= Math.max(0, AgentCombatConfig.cfg.MOB_PHYSICS_STOP_DISTANCE_X)) {
                session.setChasing(false);
            }
        } else if (distance >= Math.max(0, AgentCombatConfig.cfg.MOB_PHYSICS_RESUME_DISTANCE_X)) {
            session.setChasing(true);
        }
    }
}
