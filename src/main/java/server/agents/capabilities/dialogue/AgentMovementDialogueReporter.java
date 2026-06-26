package server.agents.capabilities.dialogue;

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
}
