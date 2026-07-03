package server.agents.capabilities.movement;

/**
 * Agent-owned baseline movement physics values used by movement profiles and the
 * legacy physics runtime while reconstruction continues.
 */
public final class AgentMovementPhysicsConfig {
    private static final int MOVEMENT_TICK_MS = 50;
    private static final int WALK_VEL_PXS = 125;
    private static final double HFORCE_PXS = 16.667;
    private static final float JUMP_SPEED_PXS = 555.0f;
    private static final float ROPE_JUMP_SPEED_PXS = 375.0f;
    private static final int ROPE_GRAB_X = 8;
    private static final int MAX_SNAP_DROP = 16;
    private static final int MAX_SLOPE_UP = 26;

    private AgentMovementPhysicsConfig() {
    }

    public static int configuredWalkVelocityPxs() {
        return WALK_VEL_PXS;
    }

    public static int configuredMovementTickMs() {
        return MOVEMENT_TICK_MS;
    }

    public static double configuredHorizontalForcePxs() {
        return HFORCE_PXS;
    }

    public static float configuredJumpSpeedPxs() {
        return JUMP_SPEED_PXS;
    }

    public static float configuredRopeJumpSpeedPxs() {
        return ROPE_JUMP_SPEED_PXS;
    }

    public static int configuredRopeGrabX() {
        return ROPE_GRAB_X;
    }

    public static int configuredMaxSnapDrop() {
        return MAX_SNAP_DROP;
    }

    public static int configuredMaxSlopeUp() {
        return MAX_SLOPE_UP;
    }
}
