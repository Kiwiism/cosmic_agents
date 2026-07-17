package server.agents.capabilities.build.profiles;

import client.Job;

public record AgentApBuildProfile(
        String profileId,
        int profileVersion,
        JobFamily jobFamily,
        StatType primaryStat,
        StatType targetStat,
        int progressionStartLevel,
        int targetAtStartLevel,
        int targetGainPerLevel,
        int targetCap,
        int supportedThroughLevel) {

    public AgentApBuildProfile {
        if (profileId == null || profileId.isBlank() || profileVersion <= 0 || jobFamily == null
                || primaryStat == null || targetStat == null || primaryStat == targetStat
                || progressionStartLevel < 1 || targetAtStartLevel < 4 || targetGainPerLevel < 0
                || targetCap < targetAtStartLevel || supportedThroughLevel < progressionStartLevel) {
            throw new IllegalArgumentException("valid AP build profile fields are required");
        }
    }

    public int targetAtLevel(int level) {
        int levels = Math.max(0, Math.min(level, supportedThroughLevel) - progressionStartLevel);
        return Math.min(targetCap, targetAtStartLevel + levels * targetGainPerLevel);
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

    public enum JobFamily {
        WARRIOR,
        BOWMAN,
        THIEF,
        MAGICIAN,
        PIRATE
    }

    public enum StatType {
        STR,
        DEX,
        INT,
        LUK
    }
}
