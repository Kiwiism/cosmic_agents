package server.agents.capabilities.dialogue;

import server.agents.capabilities.movement.AgentMovementKinematicsSnapshot;

import java.util.List;

public final class AgentMovementDialogueReporter {
    private AgentMovementDialogueReporter() {
    }

    public record MovementProfile(int totalSpeedStat,
                                  int totalJumpStat,
                                  double walkVelocityPxs,
                                  double hForcePxs,
                                  double jumpForcePerTick,
                                  double ropeJumpForcePerTick,
                                  double maxJumpHeight) {
    }

    public record MapMovementProfile(int walkStep,
                                     int climbStepPerTick,
                                     int maxJumpHorizontalTravel,
                                     int maxRopeJumpHorizontalTravel) {
    }

    public static List<String> movementStatsReport(MovementProfile profile,
                                                   int rawSpeedStat,
                                                   int rawJumpStat,
                                                   boolean movementSkillsForced,
                                                   int climbStepPerTick,
                                                   MapMovementProfile mapProfile) {
        if (profile == null) {
            return List.of(AgentDialogueCatalog.movementStatsUnavailableReply());
        }

        String speedLine = movementStatLine(profile, rawSpeedStat, rawJumpStat, movementSkillsForced);
        if (mapProfile == null) {
            return List.of(
                    speedLine,
                    AgentDialogueReportFormatter.movementWalkNoMap(
                            profile.walkVelocityPxs(), profile.hForcePxs(), climbStepPerTick),
                    AgentDialogueReportFormatter.movementJumpNoMap(
                            profile.jumpForcePerTick(),
                            profile.ropeJumpForcePerTick(),
                            profile.maxJumpHeight())
            );
        }

        return List.of(
                speedLine,
                AgentDialogueReportFormatter.movementWalkWithMap(
                        profile.walkVelocityPxs(),
                        mapProfile.walkStep(),
                        mapProfile.climbStepPerTick(),
                        profile.hForcePxs()),
                AgentDialogueReportFormatter.movementJumpWithMap(
                        profile.jumpForcePerTick(),
                        profile.ropeJumpForcePerTick(),
                        profile.maxJumpHeight(),
                        mapProfile.maxJumpHorizontalTravel(),
                        mapProfile.maxRopeJumpHorizontalTravel())
        );
    }

    private static String movementStatLine(MovementProfile profile,
                                           int rawSpeedStat,
                                           int rawJumpStat,
                                           boolean movementSkillsForced) {
        if (movementSkillsForced
                && (rawSpeedStat != profile.totalSpeedStat() || rawJumpStat != profile.totalJumpStat())) {
            return AgentDialogueReportFormatter.movementStatLineForced(
                    profile.totalSpeedStat(), profile.totalJumpStat(), rawSpeedStat, rawJumpStat);
        }
        return AgentDialogueReportFormatter.movementStatLine(profile.totalSpeedStat(), profile.totalJumpStat());
    }

    public static List<String> movementStatsReport(AgentMovementKinematicsSnapshot snapshot) {
        if (snapshot == null) {
            return movementStatsReport(null, 0, 0, false, 0, null);
        }

        AgentMovementKinematicsSnapshot.MovementProfile profile = snapshot.movementProfile();
        MovementProfile dialogueProfile =
                new MovementProfile(
                        profile.totalSpeedStat(),
                        profile.totalJumpStat(),
                        profile.walkVelocityPxs(),
                        profile.hForcePxs(),
                        profile.jumpForcePerTick(),
                        profile.ropeJumpForcePerTick(),
                        profile.maxJumpHeight());
        AgentMovementKinematicsSnapshot.MapMovementProfile mapProfile = snapshot.mapMovementProfile();
        MapMovementProfile dialogueMapProfile = mapProfile == null
                ? null
                : new MapMovementProfile(
                        mapProfile.walkStep(),
                        mapProfile.climbStepPerTick(),
                        mapProfile.maxJumpHorizontalTravel(),
                        mapProfile.maxRopeJumpHorizontalTravel());
        return movementStatsReport(
                dialogueProfile,
                snapshot.rawSpeedStat(),
                snapshot.rawJumpStat(),
                snapshot.movementSkillsForced(),
                snapshot.climbStepPerTick(),
                dialogueMapProfile);
    }
}
