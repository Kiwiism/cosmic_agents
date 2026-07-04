package server.agents.capabilities.movement;

import client.Character;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.bots.BotEntry;
import server.maps.Foothold;
import server.maps.MapleMap;

import java.awt.Point;

/**
 * Agent-owned seam for grounded movement physics while internals migrate.
 */
public final class AgentGroundPhysicsService {
    private static final double CLIENT_GROUND_STEP_MS = 8.0;
    private static final double CLIENT_GROUND_STEP_S = CLIENT_GROUND_STEP_MS / 1000.0;
    private static final double GROUNDSLIP = 3.0;
    private static final double FRICTION = 0.3;
    private static final double SLOPEFACTOR = 0.1;

    private AgentGroundPhysicsService() {
    }

    public static Foothold syncAndDetectGround(BotEntry entry, Character agent) {
        syncGroundPosition(entry, agent.getPosition().x);
        Foothold foothold = AgentGroundingService.findGroundFoothold(agent.getMap(), agent.getPosition());
        if (foothold == null) {
            beginFall(entry, agent, 0);
        }
        return foothold;
    }

    public static AgentGroundMotion applyGroundMotion(BotEntry entry, Character agent, Foothold foothold) {
        MapleMap map = agent.getMap();
        Point currentPosition = agent.getPosition();
        int desiredDirection = AgentBotMovementStateRuntime.moveDirection(entry);
        GroundStepResult step = simulateGroundMotion(map, currentPosition, foothold, desiredDirection,
                new AgentGroundTravelState(AgentBotMovementPhysicsStateRuntime.physicsX(entry),
                        AgentBotMovementPhysicsStateRuntime.horizontalSpeed(entry),
                        AgentBotMovementPhysicsStateRuntime.groundPhysicsCarryMs(entry)),
                AgentBotMovementStateRuntime.movementProfile(entry));

        if (step.lostGround()) {
            beginFall(entry, agent, step.point(), step.stepX());
            return new AgentGroundMotion(step.stepX(), true);
        }

        Point position = step.point();
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
        AgentBotMovementPhysicsStateRuntime.setHorizontalSpeed(entry, step.state().hspeed());
        AgentBotMovementPhysicsStateRuntime.setGroundPhysicsCarryMs(entry, step.state().carryMs());
        AgentBotMovementStateRuntime.setDownJumpPending(entry, false);
        AgentBotMovementStateRuntime.setMovementVelocity(entry, step.velocityX(), 0);
        AgentMovementPoseService.syncCharacterState(entry);
        return new AgentGroundMotion(step.stepX(), false);
    }

    public static void stopGroundMotion(BotEntry entry) {
        AgentBotMovementPhysicsStateRuntime.setHorizontalSpeed(entry, 0.0);
    }

    public static int velocityFromDeltaX(double deltaX) {
        return AgentMovementKinematicsService.velocityFromDeltaX(deltaX);
    }

    private static void syncGroundPosition(BotEntry entry, int x) {
        if (AgentBotMovementPhysicsStateRuntime.horizontalSpeed(entry) == 0.0
                && AgentBotMovementPhysicsStateRuntime.roundedPhysicsX(entry) != x) {
            AgentBotMovementPhysicsStateRuntime.setPhysicsX(entry, x);
        }
    }

    static GroundStepResult simulateGroundMotion(MapleMap map,
                                                 Point currentPosition,
                                                 Foothold foothold,
                                                 int desiredDirection,
                                                 AgentGroundTravelState state,
                                                 AgentMovementProfile profile) {
        if (map == null || currentPosition == null || foothold == null || state == null) {
            return new GroundStepResult(currentPosition, foothold, state, 0, 0, true);
        }

        AgentGroundTravelState displaced = applyGroundDisplacement(map, foothold, desiredDirection, state, profile);
        int newX = (int) Math.round(displaced.physX());
        int stepX = newX - currentPosition.x;
        AgentGroundCollisionService.GroundStepPreview preview =
                AgentGroundCollisionService.previewGroundStep(map, currentPosition, foothold, newX);
        if (preview == null) {
            return new GroundStepResult(currentPosition, foothold, state, 0, 0, true);
        }

        if (preview.blocked()) {
            return new GroundStepResult(currentPosition, foothold, initialGroundTravelState(currentPosition), 0, 0, false);
        }

        if (preview.lostGround()) {
            return new GroundStepResult(new Point(newX, preview.baseY()), foothold, displaced,
                    stepX, velocityFromDeltaX(displaced.physX() - currentPosition.x), true);
        }

        return new GroundStepResult(preview.point(), preview.foothold() != null ? preview.foothold() : foothold, displaced,
                stepX, velocityFromDeltaX(displaced.physX() - currentPosition.x), false);
    }

    static AgentGroundTravelState initialGroundTravelState(Point position) {
        return new AgentGroundTravelState(position.x, 0.0, 0.0);
    }

    private static AgentGroundTravelState applyGroundDisplacement(MapleMap map,
                                                                  Foothold foothold,
                                                                  int desiredDirection,
                                                                  AgentGroundTravelState state,
                                                                  AgentMovementProfile profile) {
        GroundStepCounter counter = groundPhysicsSteps(state.carryMs(), map);
        if (counter.steps() == 0) {
            return state;
        }

        double physicsX = state.physX();
        double horizontalSpeed = state.hspeed();
        for (int i = 0; i < counter.steps(); i++) {
            horizontalSpeed = applyGroundPhysicsStep(horizontalSpeed, foothold, desiredDirection, profile);
            physicsX += horizontalSpeed;
        }
        return new AgentGroundTravelState(physicsX, horizontalSpeed, counter.carryMs());
    }

    private static GroundStepCounter groundPhysicsSteps(double carryMs, MapleMap map) {
        double nextCarryMs = carryMs
                + AgentMovementPhysicsConfig.configuredMovementTickMs() * mapGroundSpeedScale(map);
        int steps = (int) (nextCarryMs / CLIENT_GROUND_STEP_MS);
        nextCarryMs -= steps * CLIENT_GROUND_STEP_MS;
        return new GroundStepCounter(steps, nextCarryMs);
    }

    private static double applyGroundPhysicsStep(double horizontalSpeed,
                                                 Foothold foothold,
                                                 int desiredDirection,
                                                 AgentMovementProfile profile) {
        double horizontalForce = desiredDirection * maxHorizontalForcePerClientStep(profile);
        if (horizontalForce == 0.0 && Math.abs(horizontalSpeed) < 0.1) {
            return 0.0;
        }

        double inertia = horizontalSpeed / GROUNDSLIP;
        double slope = clampedSlope(foothold);
        double drag = (FRICTION + SLOPEFACTOR * (1.0 + slope * -inertia)) * inertia;
        return horizontalSpeed + horizontalForce - drag;
    }

    private static double clampedSlope(Foothold foothold) {
        if (foothold == null) {
            return 0.0;
        }
        return Math.clamp(foothold.slope(), -0.5, 0.5);
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

    private static AgentMovementProfile profileOrBase(AgentMovementProfile profile) {
        return profile != null ? profile : AgentMovementProfile.base();
    }

    private static void beginFall(BotEntry entry, Character agent, int airVelocityX) {
        beginFall(entry, agent, agent.getPosition(), airVelocityX);
    }

    private static void beginFall(BotEntry entry, Character agent, Point position, int airVelocityX) {
        AgentBotClimbStateRuntime.clearBlockedRopeGrab(entry);
        agent.setPosition(new Point(position));
        AgentAirborneLaunchService.launchAirborne(entry, position, 0f, airVelocityX, false);
    }

    record GroundStepResult(Point point,
                            Foothold foothold,
                            AgentGroundTravelState state,
                            int stepX,
                            int velocityX,
                            boolean lostGround) {
    }

    private record GroundStepCounter(int steps, double carryMs) {
    }
}
