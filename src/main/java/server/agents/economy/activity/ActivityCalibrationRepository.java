package server.agents.economy.activity;

import java.util.Optional;

public interface ActivityCalibrationRepository {
    Optional<ActivityCalibration> find(String agentBuild, int mapId, int level,
                                       String jobFamily, int minimumSamples);
}
