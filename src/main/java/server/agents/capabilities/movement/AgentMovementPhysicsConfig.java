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
    private static final float CLIMB_SPEED_PXS = 100.0f;
    private static final int ROPE_GRAB_X = 8;
    private static final int MAX_SNAP_DROP = 16;
    private static final int MAX_SLOPE_UP = 26;
    private static final int STOP_DIST = 30;
    private static final int FOLLOW_DIST = 80;
    private static final int GRIND_EDGE_MARGIN = 40;
    private static final int MOB_AVOID_LOOKAHEAD_STEPS = 3;
    private static final int JUMP_Y_THRESHOLD = 30;
    private static final int TELEPORT_DIST = 4000;
    private static final int OUT_OF_BOUNDS_TELEPORT_DIST = 600;
    private static final int FOLLOW_Y_CAP = 200;
    private static final int SWIM_ARRIVAL_RADIUS_PX = 8;
    private static final int SWIM_JUMP_COOLDOWN_MS = 500;
    private static final int SWIM_LEVEL_BAND_PX = 30;
    private static final int SWIM_DOWN_BAND_PX = 120;
    private static final int SWIM_JUMP_TRIGGER_DY_PX = 100;

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

    public static float configuredClimbSpeedPxs() {
        return CLIMB_SPEED_PXS;
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

    public static int configuredStopDist() {
        return STOP_DIST;
    }

    public static int configuredFollowDist() {
        return FOLLOW_DIST;
    }

    public static int configuredGrindEdgeMargin() {
        return GRIND_EDGE_MARGIN;
    }

    public static int configuredMobAvoidLookaheadSteps() {
        return MOB_AVOID_LOOKAHEAD_STEPS;
    }

    public static int configuredJumpYThreshold() {
        return JUMP_Y_THRESHOLD;
    }

    public static int configuredTeleportDist() {
        return TELEPORT_DIST;
    }

    public static int configuredOutOfBoundsTeleportDist() {
        return OUT_OF_BOUNDS_TELEPORT_DIST;
    }

    public static int configuredFollowYCap() {
        return FOLLOW_Y_CAP;
    }

    public static int configuredSwimArrivalRadiusPx() {
        return SWIM_ARRIVAL_RADIUS_PX;
    }

    public static int configuredSwimJumpCooldownMs() {
        return SWIM_JUMP_COOLDOWN_MS;
    }

    public static int configuredSwimLevelBandPx() {
        return SWIM_LEVEL_BAND_PX;
    }

    public static int configuredSwimDownBandPx() {
        return SWIM_DOWN_BAND_PX;
    }

    public static int configuredSwimJumpTriggerDyPx() {
        return SWIM_JUMP_TRIGGER_DY_PX;
    }
}
