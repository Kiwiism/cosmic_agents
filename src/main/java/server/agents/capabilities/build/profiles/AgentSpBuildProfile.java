package server.agents.capabilities.build.profiles;

import client.Job;

import java.util.List;

public record AgentSpBuildProfile(
        String profileId,
        int profileVersion,
        JobFamily jobFamily,
        int supportedThroughLevel,
        List<LevelPlan> levels) {

    public AgentSpBuildProfile {
        if (profileId == null || profileId.isBlank() || profileVersion <= 0 || jobFamily == null
                || supportedThroughLevel < 1 || levels == null || levels.isEmpty()) {
            throw new IllegalArgumentException("valid SP build profile fields are required");
        }
        levels = List.copyOf(levels);
    }

    public boolean supports(Job job) {
        if (job == null) {
            return false;
        }
        return switch (jobFamily) {
            case WARRIOR -> job.isA(Job.WARRIOR);
            case BOWMAN -> job.isA(Job.BOWMAN);
            case THIEF -> job.isA(Job.THIEF);
            case MAGICIAN -> job.isA(Job.MAGICIAN);
            case PIRATE -> job.isA(Job.PIRATE);
        };
    }

    public record LevelPlan(int level, List<SkillPoints> allocations) {
        public LevelPlan {
            if (level < 1 || allocations == null || allocations.isEmpty()) {
                throw new IllegalArgumentException("SP level plan must have allocations");
            }
            allocations = List.copyOf(allocations);
        }
    }

    public record SkillPoints(int skillId, int points) {
        public SkillPoints {
            if (skillId <= 0 || points <= 0) {
                throw new IllegalArgumentException("SP skill points must be positive");
            }
        }
    }

    public enum JobFamily {
        WARRIOR,
        BOWMAN,
        THIEF,
        MAGICIAN,
        PIRATE
    }
}
