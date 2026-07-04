package server.agents.capabilities.movement;

import client.Character;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotCombatDamageRuntime;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.bots.BotEntry;
import server.maps.Foothold;
import server.maps.MapleMap;

import java.awt.Point;

/**
 * Agent-owned airborne movement integrator.
 */
public final class AgentAirbornePhysicsService {
    private static final double CLIENT_GROUND_STEP_MS = 8.0;
    private static final double CLIENT_GROUND_STEP_S = CLIENT_GROUND_STEP_MS / 1000.0;
    private static final double GROUNDSLIP = 3.0;
    private static final double FRICTION = 0.3;
    private static final double SLOPEFACTOR = 0.1;
    private static final float MAX_FALL_PXS = 670.0f;
    private static final double AIR_STEER_ACCEL = 0.5;
    private static final double AIR_STEER_MAX = 1.5;

    private AgentAirbornePhysicsService() {
    }

    public static AgentAirborneStepResult stepAirborne(BotEntry entry, Character agent) {
        if (AgentBotMovementStateRuntime.hasMoveDirection(entry)) {
            applyAirSteering(entry, AgentBotMovementStateRuntime.moveDirection(entry));
        }

        Point previousPosition = roundedAirPosition(entry);
        Point nextPosition = advanceAirbornePosition(entry);
        AgentJumpProbeService.AirCollision collision =
                AgentJumpProbeService.resolveAirCollision(agent.getMap(), previousPosition, nextPosition);
        if (collision.type() == AgentJumpProbeService.AirCollisionType.WALL) {
            collideWithAirWall(entry, agent, collision.point());
            return AgentAirborneStepResult.WALL;
        }
        if (collision.type() == AgentJumpProbeService.AirCollisionType.CEILING) {
            collideWithAirCeiling(entry, agent, collision.point());
            return AgentAirborneStepResult.CEILING;
        }
        if (collision.type() == AgentJumpProbeService.AirCollisionType.LAND && canLand(entry)) {
            landOnGround(entry, agent, collision.point(), collision.foothold(),
                    nextPosition.x - previousPosition.x, nextPosition.y - previousPosition.y);
            return AgentAirborneStepResult.LANDED;
        }
        applyAirbornePosition(entry, agent, nextPosition);
        return AgentAirborneStepResult.CONTINUE;
    }

    public static boolean canLand(BotEntry entry) {
        return AgentBotMovementStateRuntime.downJumpGracePeriodMs(entry) == 0L;
    }

    static void landOnGround(BotEntry entry, Character agent, Point position) {
        landOnGround(entry, agent, position, null, 0.0, 0.0);
    }

    static void landOnGround(BotEntry entry,
                             Character agent,
                             Point position,
                             Foothold foothold,
                             double incomingDeltaX,
                             double incomingDeltaY) {
        double fallDistance = AgentBotMovementPhysicsStateRuntime.hasFallPeakPhysicsY(entry)
                ? Math.max(0.0, position.y - AgentBotMovementPhysicsStateRuntime.fallPeakPhysicsY(entry))
                : 0.0;
        agent.setPosition(position);
        AgentBotMovementStateRuntime.setInAir(entry, false);
        AgentBotClimbStateRuntime.setClimbingOnRope(entry, null);
        AgentBotMovementStateRuntime.setCrouching(entry, false);
        AgentBotClimbStateRuntime.setClimbUpIntent(entry, false);
        AgentBotMovementPhysicsStateRuntime.setVerticalVelocity(entry, 0f);
        AgentBotMovementPhysicsStateRuntime.setAirVelocityX(entry, 0);
        AgentBotMovementPhysicsStateRuntime.setAirSteerVelocityX(entry, 0.0);
        AgentBotMovementPhysicsStateRuntime.setFixedAirArc(entry, false);
        AgentBotMovementPhysicsStateRuntime.setPhysicsPosition(entry, position);
        AgentBotClimbStateRuntime.clearRopeEntry(entry);
        AgentBotMovementStateRuntime.setDownJumpPending(entry, false);
        AgentBotMovementStateRuntime.setDownJumpGracePeriodMs(entry, 0L);
        AgentBotMovementPhysicsStateRuntime.setGroundPhysicsCarryMs(entry, 0.0);
        AgentBotClimbStateRuntime.clearBlockedRopeGrab(entry);
        AgentBotMovementPhysicsStateRuntime.setHorizontalSpeed(entry, landingGroundHSpeed(agent.getMap(), foothold,
                incomingDeltaX, incomingDeltaY, AgentBotMovementStateRuntime.movementProfile(entry)));
        AgentBotMovementStateRuntime.setMovementVelocity(entry,
                AgentMovementKinematicsService.velocityFromDeltaX(tickDeltaFromGroundHSpeed(agent.getMap(),
                        AgentBotMovementPhysicsStateRuntime.horizontalSpeed(entry),
                        AgentBotMovementStateRuntime.movementProfile(entry))),
                0);
        AgentMovementPoseService.syncCharacterState(entry);

        AgentBotCombatDamageRuntime.applyFallDamage(entry, agent, (float) fallDistance, AgentCombatConfig.cfg);
        AgentBotMovementPhysicsStateRuntime.resetFallPeakPhysicsY(entry);
    }

    private static void applyAirSteering(BotEntry entry, int steerDirection) {
        if (steerDirection == 0) {
            return;
        }
        double acceleration = steerDirection > 0 ? AIR_STEER_ACCEL : -AIR_STEER_ACCEL;
        AgentBotMovementPhysicsStateRuntime.addClampedAirSteerVelocityX(entry, acceleration, AIR_STEER_MAX);
        AgentBotMovementStateRuntime.setFacingDirection(entry, steerDirection > 0 ? 1 : -1);
    }

    private static Point advanceAirbornePosition(BotEntry entry) {
        double deltaX = AgentBotMovementPhysicsStateRuntime.airVelocityX(entry)
                + AgentBotMovementPhysicsStateRuntime.airSteerVelocityX(entry);
        float gravity = AgentMovementKinematicsService.gravityPerTick();
        double deltaY = AgentBotMovementPhysicsStateRuntime.verticalVelocity(entry) + 0.5f * gravity;
        AgentBotMovementPhysicsStateRuntime.addPhysicsPosition(entry, deltaX, deltaY);
        AgentBotMovementPhysicsStateRuntime.setVerticalVelocity(entry,
                Math.min(AgentBotMovementPhysicsStateRuntime.verticalVelocity(entry) + gravity, maxFallPerTick()));
        AgentBotMovementPhysicsStateRuntime.recordFallPeakPhysicsY(entry,
                AgentBotMovementPhysicsStateRuntime.physicsY(entry));

        return roundedAirPosition(entry);
    }

    private static void applyAirbornePosition(BotEntry entry, Character agent, Point position) {
        agent.setPosition(position);
        AgentBotMovementStateRuntime.setInAir(entry, true);
        AgentBotClimbStateRuntime.setClimbingOnRope(entry, null);
        AgentBotMovementStateRuntime.setCrouching(entry, false);
        int facingDirection = AgentBotMovementStateRuntime.facingDirection(entry);
        AgentBotMovementStateRuntime.setMovementVelocity(entry,
                AgentMovementKinematicsService.velocityFromDeltaX(
                        AgentBotMovementPhysicsStateRuntime.airVelocityX(entry)),
                velocityFromAirStep(AgentBotMovementPhysicsStateRuntime.verticalVelocity(entry)));
        AgentBotMovementStateRuntime.setFacingDirection(entry, facingDirection);
        AgentMovementPoseService.syncCharacterState(entry);
    }

    private static void collideWithAirWall(BotEntry entry, Character agent, Point collisionPoint) {
        AgentBotMovementPhysicsStateRuntime.setAirVelocityX(entry, 0);
        AgentBotMovementPhysicsStateRuntime.setAirSteerVelocityX(entry, 0.0);
        AgentBotMovementPhysicsStateRuntime.setFixedAirArc(entry, false);
        AgentBotMovementPhysicsStateRuntime.setPhysicsPosition(entry, collisionPoint);
        agent.setPosition(collisionPoint);
        AgentBotMovementStateRuntime.setInAir(entry, true);
        AgentBotClimbStateRuntime.setClimbingOnRope(entry, null);
        AgentBotMovementStateRuntime.setCrouching(entry, false);
        AgentBotMovementStateRuntime.setMovementVelocity(entry, 0,
                velocityFromAirStep(AgentBotMovementPhysicsStateRuntime.verticalVelocity(entry)));
        AgentMovementPoseService.syncCharacterState(entry);
    }

    private static void collideWithAirCeiling(BotEntry entry, Character agent, Point collisionPoint) {
        AgentBotMovementPhysicsStateRuntime.setVerticalVelocity(entry, 0f);
        AgentBotMovementPhysicsStateRuntime.setFixedAirArc(entry, false);
        AgentBotMovementPhysicsStateRuntime.setPhysicsPosition(entry, collisionPoint);
        agent.setPosition(collisionPoint);
        AgentBotMovementStateRuntime.setInAir(entry, true);
        AgentBotClimbStateRuntime.setClimbingOnRope(entry, null);
        AgentBotMovementStateRuntime.setCrouching(entry, false);
        AgentBotMovementStateRuntime.setMovementVelocity(entry,
                AgentMovementKinematicsService.velocityFromDeltaX(
                        AgentBotMovementPhysicsStateRuntime.airVelocityX(entry)),
                0);
        AgentMovementPoseService.syncCharacterState(entry);
    }

    private static Point roundedAirPosition(BotEntry entry) {
        return AgentBotMovementPhysicsStateRuntime.roundedPhysicsPosition(entry);
    }

    private static int velocityFromAirStep(float airVelocityPerTick) {
        return Math.round(airVelocityPerTick * (1000f / AgentMovementPhysicsConfig.configuredMovementTickMs()));
    }

    private static float maxFallPerTick() {
        return MAX_FALL_PXS * AgentMovementPhysicsConfig.configuredMovementTickMs() / 1000f;
    }

    private static double landingGroundHSpeed(MapleMap map,
                                              Foothold foothold,
                                              double incomingDeltaX,
                                              double incomingDeltaY,
                                              AgentMovementProfile profile) {
        double landingDeltaX = incomingDeltaX;
        if (foothold != null && !foothold.isWall() && foothold.slope() != 0.0) {
            double tangentX = foothold.getX2() - foothold.getX1();
            double tangentY = foothold.getY2() - foothold.getY1();
            double tangentLength = Math.hypot(tangentX, tangentY);
            if (tangentLength > 0.0) {
                double unitX = tangentX / tangentLength;
                double unitY = tangentY / tangentLength;
                double dot = incomingDeltaX * unitX + incomingDeltaY * unitY;
                landingDeltaX = unitX * dot;
            }
        }

        double maxDeltaPerTick = Math.max(1.0, AgentMovementKinematicsService.walkStep(map, profile));
        landingDeltaX = Math.clamp(landingDeltaX, -maxDeltaPerTick, maxDeltaPerTick);
        return groundHSpeedFromTickDelta(map, landingDeltaX, profile);
    }

    private static double groundHSpeedFromTickDelta(MapleMap map, double deltaXPerTick, AgentMovementProfile profile) {
        double stepsPerTick = Math.max(1.0,
                (AgentMovementPhysicsConfig.configuredMovementTickMs() * mapGroundSpeedScale(map)) / CLIENT_GROUND_STEP_MS);
        return Math.clamp(deltaXPerTick / stepsPerTick,
                -maxHorizontalSpeedPerClientStep(profile),
                maxHorizontalSpeedPerClientStep(profile));
    }

    private static double tickDeltaFromGroundHSpeed(MapleMap map, double groundHSpeed, AgentMovementProfile profile) {
        double stepsPerTick = Math.max(1.0,
                (AgentMovementPhysicsConfig.configuredMovementTickMs() * mapGroundSpeedScale(map)) / CLIENT_GROUND_STEP_MS);
        double clampedHSpeed = Math.clamp(groundHSpeed,
                -maxHorizontalSpeedPerClientStep(profile),
                maxHorizontalSpeedPerClientStep(profile));
        return clampedHSpeed * stepsPerTick;
    }

    private static double mapGroundSpeedScale(MapleMap map) {
        float footholdSpeed = map.getFootholdSpeed();
        if (footholdSpeed <= 0.0f) {
            return 1.0;
        }
        return footholdSpeed;
    }

    private static double maxHorizontalForcePerClientStep(AgentMovementProfile profile) {
        return profileOrBase(profile).hForcePxs() * CLIENT_GROUND_STEP_S;
    }

    private static double maxHorizontalSpeedPerClientStep(AgentMovementProfile profile) {
        return maxHorizontalForcePerClientStep(profile) * GROUNDSLIP / (FRICTION + SLOPEFACTOR);
    }

    private static AgentMovementProfile profileOrBase(AgentMovementProfile profile) {
        return profile != null ? profile : AgentMovementProfile.base();
    }
}
