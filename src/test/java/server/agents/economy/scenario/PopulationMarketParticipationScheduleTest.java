package server.agents.economy.scenario;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PopulationMarketParticipationScheduleTest {
    @Test
    void compensatesForOnboardingCohortsThenConvergesToWholePopulationTarget() {
        EconomyEngineConfig config = new EconomyConfigLoader().load(
                java.nio.file.Path.of("config/economy/economy-commerce-observe-30day.yaml")).config();
        Instant start = Instant.parse(config.clock.logicalStart);
        PopulationMarketParticipationSchedule schedule = new PopulationMarketParticipationSchedule(
                config.population, start, config.scenario.seed);

        assertTrue(schedule.eligibleTarget("agent-1", start, .40d).isEmpty());
        assertEquals(.80d, schedule.eligibleTarget("agent-1", start.plusSeconds(86_400), .40d)
                .orElseThrow(), 0.0001d);
        assertTrue(schedule.eligibleTarget("agent-11", start.plusSeconds(86_400), .40d).isEmpty());
        assertEquals(.40d, schedule.eligibleTarget("agent-1", start.plusSeconds(10L * 86_400), .40d)
                .orElseThrow(), 0.0001d);
    }
}
