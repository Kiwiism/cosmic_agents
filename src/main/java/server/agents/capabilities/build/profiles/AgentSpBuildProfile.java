package server.agents.capabilities.build.profiles;

import client.Job;

import java.util.List;
import java.util.Map;

public record AgentSpBuildProfile(
        String profileId,
        int profileVersion,
        JobFamily jobFamily,
        Map<Integer, Integer> inheritedSkillLevels,
        int supportedThroughLevel,
        List<LevelPlan> levels,
        int exactJobId,
        int startingLevel,
        int entrySp,
        List<AllocationSegment> segments,
        List<Integer> dumpSkillIds) {

    public AgentSpBuildProfile {
        levels = levels == null ? List.of() : List.copyOf(levels);
        segments = segments == null ? List.of() : List.copyOf(segments);
        dumpSkillIds = dumpSkillIds == null ? List.of() : List.copyOf(dumpSkillIds);
        if (profileId == null || profileId.isBlank() || profileVersion <= 0 || jobFamily == null
                || supportedThroughLevel < 1 || (levels.isEmpty() == segments.isEmpty())) {
            throw new IllegalArgumentException("valid SP build profile fields are required");
        }
        inheritedSkillLevels = inheritedSkillLevels == null
                ? Map.of() : Map.copyOf(inheritedSkillLevels);
        if (!segments.isEmpty() && (exactJobId <= 0 || startingLevel <= 0 || entrySp <= 0
                || startingLevel > supportedThroughLevel)) {
            throw new IllegalArgumentException("ordered SP profiles require an exact job and entry grant");
        }
    }

    public AgentSpBuildProfile(String profileId, int profileVersion, JobFamily jobFamily,
                               int supportedThroughLevel, List<LevelPlan> levels) {
        this(profileId, profileVersion, jobFamily, Map.of(), supportedThroughLevel, levels,
                0, 0, 0, List.of(), List.of());
    }

    public AgentSpBuildProfile(String profileId, int profileVersion, JobFamily jobFamily,
                               Map<Integer, Integer> inheritedSkillLevels,
                               int supportedThroughLevel, List<LevelPlan> levels) {
        this(profileId, profileVersion, jobFamily, inheritedSkillLevels, supportedThroughLevel,
                levels, 0, 0, 0, List.of(), List.of());
    }

    public boolean supports(Job job) {
        if (job == null) {
            return false;
        }
        if (exactJobId > 0) {
            Job exactJob = Job.getById(exactJobId);
            return exactJob != null && job.isA(exactJob);
        }
        return switch (jobFamily) {
            case WARRIOR -> job.isA(Job.WARRIOR);
            case BOWMAN -> job.isA(Job.BOWMAN);
            case THIEF -> job.isA(Job.THIEF);
            case MAGICIAN -> job.isA(Job.MAGICIAN);
            case PIRATE -> job.isA(Job.PIRATE);
        };
    }

    public boolean isMapleRoyalsOptimal2026() {
        return profileId.startsWith("mapleroyals-optimal-2026-");
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

    public record AllocationSegment(int minimumLevel, int skillId, int points) {
        public AllocationSegment {
            if (minimumLevel <= 0 || skillId <= 0 || points <= 0) {
                throw new IllegalArgumentException("ordered SP allocation segment must be positive");
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
