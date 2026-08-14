package server.agents.economy.scenario;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PopulationAdmissionPlannerTest {
    @Test
    void defaultScenarioStartsAtFiftyAndAddsTenDailyToTwoHundred() {
        EconomyEngineConfig config = new EconomyConfigLoader().load().config();
        Instant start = Instant.parse(config.clock.logicalStart);
        var admissions = new PopulationAdmissionPlanner().plan(
                config.population, start, new NamedRandomStreams(config.scenario.seed));

        assertEquals(200, admissions.size());
        assertEquals(50, admissions.stream().filter(a -> a.admittedAt().equals(start)).count());
        assertTrue(admissions.get(199).admittedAt().isAfter(start.plus(java.time.Duration.ofDays(15))));
        assertTrue(admissions.get(199).admittedAt().isBefore(start.plus(java.time.Duration.ofDays(16))));
        assertTrue(admissions.stream().allMatch(a -> a.dailyActivityFraction() > 0));
    }
}
