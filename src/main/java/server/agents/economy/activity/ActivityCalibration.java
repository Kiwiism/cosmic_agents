package server.agents.economy.activity;

import java.time.Instant;
import java.util.Map;

/** Evidence from real autonomous-agent sessions, suitable for versioned offscreen planning. */
public record ActivityCalibration(String calibrationId, String agentBuild, int mapId,
                                  int level, String jobFamily, Instant measuredAt,
                                  int sampleSessions, double killsPerMinute,
                                  Map<Integer, Double> monsterKillShare,
                                  Map<Integer, Double> itemUsePerMinute,
                                  double deathProbabilityPerHour) {
    public ActivityCalibration {
        if (calibrationId == null || calibrationId.isBlank() || mapId <= 0 || level <= 0
                || measuredAt == null || sampleSessions <= 0 || killsPerMinute < 0
                || deathProbabilityPerHour < 0 || deathProbabilityPerHour > 1) {
            throw new IllegalArgumentException("invalid activity calibration");
        }
        monsterKillShare = Map.copyOf(monsterKillShare);
        itemUsePerMinute = Map.copyOf(itemUsePerMinute);
    }
}
