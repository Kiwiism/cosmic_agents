package server.agents.capabilities.movement;

import server.maps.MapleMap;

public final class AgentMovementKinematicsService {
    static final double CLIENT_GROUND_STEP_MS = 8.0;
    private static final double CLIENT_GROUND_STEP_S = CLIENT_GROUND_STEP_MS / 1000.0;
    private static final double GROUNDSLIP = 3.0;
    private static final double FRICTION = 0.3;
    private static final double SLOPEFACTOR = 0.1;

    private AgentMovementKinematicsService() {
    }

    public static int walkStep(MapleMap map) {
        return walkStep(map, AgentMovementProfile.base());
    }

    public static int walkStep(MapleMap map, AgentMovementProfile profile) {
        double step = maxHorizontalSpeedPerClientStep(profile)
                * AgentMovementPhysicsConfig.configuredMovementTickMs()
                * mapGroundSpeedScale(map)
                / CLIENT_GROUND_STEP_MS;
        return Math.max(1, (int) Math.round(step));
    }

    public static int climbStepPerTick() {
        return Math.max(1, Math.round(AgentMovementPhysicsConfig.configuredClimbSpeedPxs()
                * AgentMovementPhysicsConfig.configuredMovementTickMs() / 1000.0f));
    }

    public static int launchRunwayPx(MapleMap map, AgentMovementProfile profile) {
        int step = walkStep(map, profile);
        return Math.max(40, step * 6);
    }

    public static int velocityFromDeltaX(double deltaX) {
        return (int) Math.round(deltaX * (1000.0 / AgentMovementPhysicsConfig.configuredMovementTickMs()));
    }

    public static float jumpForcePerTick(AgentMovementProfile profile) {
        return profileOrBase(profile).jumpSpeedPxs() * tickS();
    }

    public static float ropeJumpForcePerTick(AgentMovementProfile profile) {
        return profileOrBase(profile).ropeJumpSpeedPxs() * tickS();
    }

    public static float gravityPerTick() {
        float t = tickS();
        return AgentMovementPhysicsConfig.configuredGravityPxs2() * t * t;
    }

    public static float calculateMaxJumpHeight(AgentMovementProfile profile) {
        float jumpForce = jumpForcePerTick(profile);
        return jumpForce * jumpForce / (2 * gravityPerTick());
    }

    public static int maxJumpHorizontalTravel(MapleMap map, AgentMovementProfile profile) {
        return maxHorizontalTravel(map, profile, jumpForcePerTick(profile));
    }

    public static int maxRopeJumpHorizontalTravel(MapleMap map, AgentMovementProfile profile) {
        return maxHorizontalTravel(map, profile, ropeJumpForcePerTick(profile));
    }

    public static int maxRopeGrabSimulationHorizontalTravel(MapleMap map, AgentMovementProfile profile) {
        int maxTicks = Math.max(1, 1500 / AgentMovementPhysicsConfig.configuredMovementTickMs());
        return walkStep(map, profile) * maxTicks;
    }

    private static AgentMovementProfile profileOrBase(AgentMovementProfile profile) {
        return profile != null ? profile : AgentMovementProfile.base();
    }

    private static float tickS() {
        return AgentMovementPhysicsConfig.configuredMovementTickMs() / 1000f;
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

    static double maxHorizontalSpeedPerClientStep(AgentMovementProfile profile) {
        return maxHorizontalForcePerClientStep(profile) * GROUNDSLIP / (FRICTION + SLOPEFACTOR);
    }

    private static int maxHorizontalTravel(MapleMap map, AgentMovementProfile profile, float launchSpeedPerTick) {
        int airtimeTicks = Math.max(1, (int) Math.ceil((2 * launchSpeedPerTick) / gravityPerTick()));
        return walkStep(map, profile) * airtimeTicks;
    }
}
