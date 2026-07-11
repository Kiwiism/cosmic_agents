package server.agents.capabilities.movement;

import client.Character;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;
import server.maps.MapleMap;

import java.awt.Point;

/**
 * Agent-owned swim movement integrator.
 */
public final class AgentSwimPhysicsService {
    private static final float SWIM_VEL_PXS = 140.0f;
    private static final float SWIM_GRAVITY_PXS2 = 590.0f;
    private static final float SWIM_FRICTION_HZ = 4.21f;
    private static final float SWIM_ACCEL_PXS2 = 600.0f;
    private static final float SWIM_MAX_SPEED_PXS = 800.0f;
    private static final float SWIM_JUMP_BURST_PXS = 1000.0f;
    private static final float SWIM_UP_THRUST_PXS2 = 412.0f;
    private static final float SWIM_DOWN_THRUST_PXS2 = 295.0f;
    private static final float SWIM_FREE_MAX_SINK_PXS = 140.0f;
    private static final float SWIM_DOWN_MAX_SPEED_PXS = 210.0f;
    private static final float SWIM_UP_MAX_SINK_PXS = 42.0f;

    private AgentSwimPhysicsService() {
    }

    public static void applySwimMotion(AgentRuntimeEntry entry) {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        MapleMap map = agent.getMap();
        Point position = agent.getPosition();
        double tickSeconds = AgentMovementPhysicsConfig.configuredMovementTickMs() / 1000.0;

        if (!AgentSwimStateRuntime.swimming(entry)) {
            AgentMovementPhysicsStateRuntime.setPhysicsPosition(entry, position);
            AgentMovementPhysicsStateRuntime.setAirVelocityX(entry, 0);
            AgentMovementPhysicsStateRuntime.setAirSteerVelocityX(entry, 0.0);
            AgentMovementPhysicsStateRuntime.setFixedAirArc(entry, false);
            AgentMovementStateRuntime.setDownJumpPending(entry, false);
            AgentMovementStateRuntime.setDownJumpGracePeriodMs(entry, 0L);
        } else if (Math.abs(AgentMovementPhysicsStateRuntime.physicsX(entry) - position.x) > 2
                || Math.abs(AgentMovementPhysicsStateRuntime.physicsY(entry) - position.y) > 2) {
            AgentMovementPhysicsStateRuntime.setPhysicsPosition(entry, position);
        }

        double velocityX = AgentMovementPhysicsStateRuntime.horizontalSpeed(entry);
        double velocityY = AgentMovementPhysicsStateRuntime.verticalVelocity(entry);

        if (AgentSwimStateRuntime.swimJumpRequested(entry)) {
            float burst = SWIM_JUMP_BURST_PXS;
            burst *= (float) AgentMovementStateRuntime.movementProfile(entry).speedMultiplier();
            velocityY = -burst;
            AgentSwimStateRuntime.setSwimJumpRequested(entry, false);
        }

        if (AgentSwimStateRuntime.swimMoveDirection(entry) != 0) {
            double accelerationStep = SWIM_ACCEL_PXS2 * tickSeconds
                    * Integer.signum(AgentSwimStateRuntime.swimMoveDirection(entry));
            velocityX += accelerationStep;
        }

        double dragRetention = Math.max(0.0, 1.0 - SWIM_FRICTION_HZ * tickSeconds);
        velocityX *= dragRetention;
        velocityY *= dragRetention;

        velocityY += SWIM_GRAVITY_PXS2 * tickSeconds;

        if (AgentSwimStateRuntime.swimVerticalHold(entry) < 0) {
            velocityY -= SWIM_UP_THRUST_PXS2 * tickSeconds;
        } else if (AgentSwimStateRuntime.swimVerticalHold(entry) > 0) {
            velocityY += SWIM_DOWN_THRUST_PXS2 * tickSeconds;
        }

        velocityX = Math.max(-SWIM_MAX_SPEED_PXS, Math.min(SWIM_MAX_SPEED_PXS, velocityX));
        if (AgentSwimStateRuntime.swimMoveDirection(entry) != 0) {
            double cap = SWIM_VEL_PXS;
            if (velocityX > cap && AgentSwimStateRuntime.swimMoveDirection(entry) > 0) {
                velocityX = cap;
            }
            if (velocityX < -cap && AgentSwimStateRuntime.swimMoveDirection(entry) < 0) {
                velocityX = -cap;
            }
        }

        double sinkCap = switch (Integer.signum(AgentSwimStateRuntime.swimVerticalHold(entry))) {
            case -1 -> SWIM_UP_MAX_SINK_PXS;
            case 1 -> SWIM_DOWN_MAX_SPEED_PXS;
            default -> SWIM_FREE_MAX_SINK_PXS;
        };
        velocityY = Math.max(-SWIM_MAX_SPEED_PXS, Math.min(sinkCap, velocityY));

        double nextX = AgentMovementPhysicsStateRuntime.physicsX(entry) + velocityX * tickSeconds;
        double nextY = AgentMovementPhysicsStateRuntime.physicsY(entry) + velocityY * tickSeconds;

        boolean landed = false;
        Foothold landingFoothold = null;
        double landingDeltaX = 0.0;
        double landingDeltaY = 0.0;
        Point previousPoint = AgentMovementPhysicsStateRuntime.roundedPhysicsPosition(entry);
        Point nextPoint = new Point((int) Math.round(nextX), (int) Math.round(nextY));
        AgentJumpProbeService.AirCollision collision =
                AgentJumpProbeService.resolveAirCollision(map, previousPoint, nextPoint);
        if (collision.type() == AgentJumpProbeService.AirCollisionType.LAND) {
            nextX = collision.point().x;
            nextY = collision.point().y;
            velocityY = 0.0;
            landed = true;
            landingFoothold = collision.foothold();
            landingDeltaX = nextX - previousPoint.x;
            landingDeltaY = nextY - previousPoint.y;
        } else if (collision.type() == AgentJumpProbeService.AirCollisionType.WALL) {
            nextX = collision.point().x;
            velocityX = 0.0;
            AgentSwimStateRuntime.setSwimWallBlocked(
                    entry, AgentSwimStateRuntime.swimMoveDirection(entry) != 0);
        } else {
            AgentSwimStateRuntime.setSwimWallBlocked(entry, false);
        }

        if (AgentSwimStateRuntime.swimMoveDirection(entry) > 0) {
            AgentMovementStateRuntime.setFacingDirection(entry, 1);
        } else if (AgentSwimStateRuntime.swimMoveDirection(entry) < 0) {
            AgentMovementStateRuntime.setFacingDirection(entry, -1);
        }

        if (landed) {
            AgentSwimStateRuntime.setSwimming(entry, false);
            AgentAirbornePhysicsService.landOnGround(entry, agent,
                    new Point((int) Math.round(nextX), (int) Math.round(nextY)),
                    landingFoothold, landingDeltaX, landingDeltaY);
            return;
        }

        AgentMovementPhysicsStateRuntime.setHorizontalSpeed(entry, velocityX);
        AgentMovementPhysicsStateRuntime.setVerticalVelocity(entry, (float) velocityY);
        AgentMovementPhysicsStateRuntime.setPhysicsPosition(entry, nextX, nextY);
        AgentMovementStateRuntime.setCrouching(entry, false);
        AgentMovementStateRuntime.setMovementVelocity(entry, (int) Math.round(velocityX), (int) Math.round(velocityY));
        AgentSwimStateRuntime.setSwimming(entry, true);
        AgentMovementStateRuntime.setInAir(entry, true);

        agent.setPosition(new Point((int) Math.round(nextX), (int) Math.round(nextY)));
    }
}
