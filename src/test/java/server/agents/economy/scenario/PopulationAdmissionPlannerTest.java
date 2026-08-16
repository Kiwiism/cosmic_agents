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

    @Test
    void zeroPopulationCanStillGrowAfterTheInitialBoundary() {
        EconomyEngineConfig config = new EconomyConfigLoader().load().config();
        config.population.initialAgents = 0;
        config.population.maximumAgents = 20;
        config.population.growth.amount = 10;
        Instant start = Instant.parse(config.clock.logicalStart);

        var admissions = new PopulationAdmissionPlanner().plan(
                config.population, start, new NamedRandomStreams(config.scenario.seed));

        assertEquals(20, admissions.size());
        assertTrue(admissions.stream().noneMatch(admission -> admission.admittedAt().equals(start)));
        assertEquals(10, admissions.stream().filter(admission ->
                admission.admittedAt().isAfter(start.plus(java.time.Duration.ofDays(1)))
                        && admission.admittedAt().isBefore(start.plus(java.time.Duration.ofDays(2)))).count());
    }

    @Test
    void differentSeedsProduceDifferentProfilesWithoutChangingPopulationBounds() {
        EconomyEngineConfig config = new EconomyConfigLoader().load().config();
        Instant start = Instant.parse(config.clock.logicalStart);

        var first = new PopulationAdmissionPlanner().plan(
                config.population, start, new NamedRandomStreams(config.scenario.seed));
        var second = new PopulationAdmissionPlanner().plan(
                config.population, start, new NamedRandomStreams(config.scenario.seed + 1));

        assertEquals(200, first.size());
        assertEquals(200, second.size());
        assertNotEquals(first.stream().map(PopulationAdmissionPlanner.Admission::profile).toList(),
                second.stream().map(PopulationAdmissionPlanner.Admission::profile).toList());
    }
}
