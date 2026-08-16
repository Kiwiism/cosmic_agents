package server.agents.economy.integration.cosmic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import server.agents.economy.activity.VictoriaActivityMapCatalog;
import server.agents.economy.persistence.EconomyPostgresDataSource;
import server.agents.economy.persistence.JdbcActivityCalibrationRepository;
import server.agents.economy.scenario.EconomyConfigLoader;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "ECONOMY_DB_HOST", matches = ".+")
class EconomyLiveCalibrationIntegrationTest {
    @Test
    void currentLevelFifteenEvidenceCoversEveryConfiguredJobFamily() {
        var config = new EconomyConfigLoader().load().config();
        var maps = new VictoriaActivityMapCatalog(config.activity.mapCatalogResource);
        assertTrue(maps.candidates(15).stream().anyMatch(map -> map.mapId() == 103000101));

        try (var database = EconomyPostgresDataSource.fromEnvironment()) {
            var repository = new JdbcActivityCalibrationRepository(database);
            for (String job : config.population.classDistribution.keySet()) {
                assertTrue(repository.find(config.activity.agentBuild, 103000101, 15, job,
                                config.activity.minimumCalibrationSamples).isPresent(),
                        () -> "missing live calibration for " + job);
            }
        }
    }
}
