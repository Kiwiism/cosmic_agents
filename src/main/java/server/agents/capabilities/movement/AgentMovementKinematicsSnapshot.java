package server.agents.capabilities.movement;

public record AgentMovementKinematicsSnapshot(
        MovementProfile movementProfile,
        int rawSpeedStat,
        int rawJumpStat,
        boolean movementSkillsForced,
        int climbStepPerTick,
        MapMovementProfile mapMovementProfile
) {
    public record MovementProfile(
            int totalSpeedStat,
            int totalJumpStat,
            double walkVelocityPxs,
            double hForcePxs,
            double jumpForcePerTick,
            double ropeJumpForcePerTick,
            double maxJumpHeight
    ) {
    }

    public record MapMovementProfile(
            int walkStep,
            int climbStepPerTick,
            int maxJumpHorizontalTravel,
            int maxRopeJumpHorizontalTravel
    ) {
    }
}
