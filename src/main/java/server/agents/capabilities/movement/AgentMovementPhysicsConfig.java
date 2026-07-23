package server.agents.capabilities.movement;

/**
 * Agent-owned baseline movement physics values used by movement profiles and the
 * legacy physics runtime while reconstruction continues.
 */
public final class AgentMovementPhysicsConfig {
    private static final int MOVEMENT_TICK_MS = config.AgentTuning.intValue("server.agents.capabilities.movement.AgentMovementPhysicsConfig.MOVEMENT_TICK_MS");
    private static final int WALK_VEL_PXS = config.AgentTuning.intValue("server.agents.capabilities.movement.AgentMovementPhysicsConfig.WALK_VEL_PXS");
    private static final double HFORCE_PXS = config.AgentTuning.doubleValue("server.agents.capabilities.movement.AgentMovementPhysicsConfig.HFORCE_PXS");
    private static final float GRAVITY_PXS2 = config.AgentTuning.floatValue("server.agents.capabilities.movement.AgentMovementPhysicsConfig.GRAVITY_PXS2");
    private static final float JUMP_SPEED_PXS = config.AgentTuning.floatValue("server.agents.capabilities.movement.AgentMovementPhysicsConfig.JUMP_SPEED_PXS");
    private static final float DOWN_JUMP_SPEED_PXS = config.AgentTuning.floatValue("server.agents.capabilities.movement.AgentMovementPhysicsConfig.DOWN_JUMP_SPEED_PXS");
    private static final float ROPE_JUMP_SPEED_PXS = config.AgentTuning.floatValue("server.agents.capabilities.movement.AgentMovementPhysicsConfig.ROPE_JUMP_SPEED_PXS");
    private static final float CLIMB_SPEED_PXS = config.AgentTuning.floatValue("server.agents.capabilities.movement.AgentMovementPhysicsConfig.CLIMB_SPEED_PXS");
    private static final int ROPE_GRAB_X = config.AgentTuning.intValue("server.agents.capabilities.movement.AgentMovementPhysicsConfig.ROPE_GRAB_X");
    private static final int MAX_SNAP_DROP = config.AgentTuning.intValue("server.agents.capabilities.movement.AgentMovementPhysicsConfig.MAX_SNAP_DROP");
    private static final int MAX_SLOPE_UP = config.AgentTuning.intValue("server.agents.capabilities.movement.AgentMovementPhysicsConfig.MAX_SLOPE_UP");
    private static final int STOP_DIST = config.AgentTuning.intValue("server.agents.capabilities.movement.AgentMovementPhysicsConfig.STOP_DIST");
    private static final int FOLLOW_DIST = config.AgentTuning.intValue("server.agents.capabilities.movement.AgentMovementPhysicsConfig.FOLLOW_DIST");
    private static final int GRIND_EDGE_MARGIN = config.AgentTuning.intValue("server.agents.capabilities.movement.AgentMovementPhysicsConfig.GRIND_EDGE_MARGIN");
    private static final int MOB_AVOID_LOOKAHEAD_STEPS = config.AgentTuning.intValue("server.agents.capabilities.movement.AgentMovementPhysicsConfig.MOB_AVOID_LOOKAHEAD_STEPS");
    private static final int JUMP_Y_THRESHOLD = config.AgentTuning.intValue("server.agents.capabilities.movement.AgentMovementPhysicsConfig.JUMP_Y_THRESHOLD");
    private static final int TELEPORT_DIST = config.AgentTuning.intValue("server.agents.capabilities.movement.AgentMovementPhysicsConfig.TELEPORT_DIST");
    private static final int OUT_OF_BOUNDS_TELEPORT_DIST = config.AgentTuning.intValue("server.agents.capabilities.movement.AgentMovementPhysicsConfig.OUT_OF_BOUNDS_TELEPORT_DIST");
    private static final int FOLLOW_Y_CAP = config.AgentTuning.intValue("server.agents.capabilities.movement.AgentMovementPhysicsConfig.FOLLOW_Y_CAP");
    private static final int DOWN_JUMP_GRACE_MS = config.AgentTuning.intValue("server.agents.capabilities.movement.AgentMovementPhysicsConfig.DOWN_JUMP_GRACE_MS");
    private static final int SWIM_ARRIVAL_RADIUS_PX = config.AgentTuning.intValue("server.agents.capabilities.movement.AgentMovementPhysicsConfig.SWIM_ARRIVAL_RADIUS_PX");
    private static final int SWIM_JUMP_COOLDOWN_MS = config.AgentTuning.intValue("server.agents.capabilities.movement.AgentMovementPhysicsConfig.SWIM_JUMP_COOLDOWN_MS");
    private static final int SWIM_LEVEL_BAND_PX = config.AgentTuning.intValue("server.agents.capabilities.movement.AgentMovementPhysicsConfig.SWIM_LEVEL_BAND_PX");
    private static final int SWIM_DOWN_BAND_PX = config.AgentTuning.intValue("server.agents.capabilities.movement.AgentMovementPhysicsConfig.SWIM_DOWN_BAND_PX");
    private static final int SWIM_JUMP_TRIGGER_DY_PX = config.AgentTuning.intValue("server.agents.capabilities.movement.AgentMovementPhysicsConfig.SWIM_JUMP_TRIGGER_DY_PX");

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

    public static float configuredGravityPxs2() {
        return GRAVITY_PXS2;
    }

    public static float configuredJumpSpeedPxs() {
        return JUMP_SPEED_PXS;
    }

    public static float configuredDownJumpSpeedPxs() {
        return DOWN_JUMP_SPEED_PXS;
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

    public static int configuredDownJumpGraceMs() {
        return DOWN_JUMP_GRACE_MS;
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
